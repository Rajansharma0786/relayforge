package dev.relayforge.delivery;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliverySchemaUpgradeTest {
    private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-11-05T10:00:00Z");

    @Test
    void preservesExistingJobsAndKeepsNewJobsCompatibleWithTheIntakeInsert() {
        try (var database = database()) {
            var jdbc = new JdbcTemplate(database);
            Flyway.configure().dataSource(database).target("1").load().migrate();
            UUID existing = insertPendingJob(jdbc);

            Flyway.configure().dataSource(database).load().migrate();

            assertPendingJob(jdbc, existing);
            assertThat(jdbc.queryForObject("SELECT next_attempt_at FROM delivery_jobs WHERE event_id = ?",
                    OffsetDateTime.class, existing)).isEqualTo(CREATED_AT);
            UUID newJob = insertPendingJob(jdbc);
            assertPendingJob(jdbc, newJob);
            assertThat(jdbc.queryForObject("SELECT next_attempt_at FROM delivery_jobs WHERE event_id = ?",
                    OffsetDateTime.class, newJob)).isNotNull();
        }
    }

    @Test
    void enforcesLeaseStateAndKeepsOneHistoryRowPerAttempt() {
        try (var database = database()) {
            Flyway.configure().dataSource(database).load().migrate();
            var jdbc = new JdbcTemplate(database);
            UUID job = insertPendingJob(jdbc);

            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE delivery_jobs SET status = 'IN_PROGRESS' WHERE event_id = ?", job))
                    .isInstanceOf(DataIntegrityViolationException.class);
            jdbc.update("""
                    UPDATE delivery_jobs SET status = 'IN_PROGRESS', attempt_count = 1, lease_expires_at = ?
                    WHERE event_id = ?
                    """, CREATED_AT.plusMinutes(1), job);
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO delivery_attempts (event_id, attempt_number, started_at, outcome)
                    VALUES (?, 1, ?, 'SUCCEEDED')
                    """, job, CREATED_AT)).isInstanceOf(DataIntegrityViolationException.class);

            String startAttempt = "INSERT INTO delivery_attempts (event_id, attempt_number, started_at) VALUES (?, 1, ?)";
            jdbc.update(startAttempt, job, CREATED_AT);
            assertThatThrownBy(() -> jdbc.update(startAttempt, job, CREATED_AT))
                    .isInstanceOf(DataIntegrityViolationException.class);
            jdbc.update("""
                    UPDATE delivery_attempts SET outcome = 'SUCCEEDED', finished_at = ?, http_status = 204
                    WHERE event_id = ? AND attempt_number = 1
                    """, CREATED_AT.plusSeconds(1), job);
            assertThat(jdbc.queryForObject("SELECT outcome FROM delivery_attempts WHERE event_id = ?",
                    String.class, job)).isEqualTo("SUCCEEDED");
        }
    }

    private static SingleConnectionDataSource database() {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
        return new SingleConnectionDataSource(url, "sa", "", true);
    }

    private static UUID insertPendingJob(JdbcTemplate jdbc) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO delivery_jobs(event_id, event_type, destination, payload, created_at)
                VALUES (?, 'invoice.paid', 'https://example.com/hooks', '{}', ?)
                """, id, CREATED_AT);
        return id;
    }

    private static void assertPendingJob(JdbcTemplate jdbc, UUID job) {
        assertThat(jdbc.queryForObject("SELECT status FROM delivery_jobs WHERE event_id = ?", String.class, job))
                .isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM delivery_jobs WHERE event_id = ?",
                Integer.class, job)).isZero();
        assertThat(jdbc.queryForObject("SELECT lease_expires_at FROM delivery_jobs WHERE event_id = ?",
                OffsetDateTime.class, job)).isNull();
    }
}
