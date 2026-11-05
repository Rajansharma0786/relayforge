package dev.relayforge.events;

import java.net.http.HttpClient;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "relayforge.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);
    private final JdbcTemplate jdbc;
    private final EventService events;
    private final TransactionTemplate transactions;
    private final RestClient delivery;

    public OutboxDispatcher(JdbcTemplate jdbc, EventService events, TransactionTemplate transactions,
                            RestClient.Builder builder,
                            @Value("${relayforge.delivery-url}") String url,
                            @Value("${relayforge.internal-token}") String token) {
        if (token.isBlank()) throw new IllegalArgumentException("Configure INTERNAL_SERVICE_TOKEN.");
        this.jdbc = jdbc; this.events = events; this.transactions = transactions;
        var requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.delivery = builder.baseUrl(url).requestFactory(requestFactory)
                .defaultHeader("X-RelayForge-Token", token).build();
    }

    @Scheduled(fixedDelayString = "${relayforge.outbox.delay-ms:3000}")
    public void forwardDueEvents() {
        List<EventView> due = jdbc.query("""
                SELECT e.* FROM events e JOIN event_outbox o ON e.id=o.event_id
                WHERE o.forwarded_at IS NULL AND o.next_attempt_at<=?
                ORDER BY e.created_at LIMIT 20
                """, events::map, Timestamp.from(Instant.now()));
        for (EventView event : due) {
            try {
                delivery.post().uri("/internal/deliveries").body(event).retrieve().toBodilessEntity();
                transactions.executeWithoutResult(status -> {
                    jdbc.update("UPDATE event_outbox SET forwarded_at=? WHERE event_id=?",
                            Timestamp.from(Instant.now()), event.id());
                    jdbc.update("UPDATE events SET status='FORWARDED' WHERE id=?", event.id());
                });
            } catch (RuntimeException failed) {
                int attempts = jdbc.queryForObject("SELECT attempts FROM event_outbox WHERE event_id=?",
                        Integer.class, event.id());
                long delay = Math.min(60, 1L << Math.min(attempts + 1, 6));
                jdbc.update("UPDATE event_outbox SET attempts=attempts+1,next_attempt_at=? WHERE event_id=? AND forwarded_at IS NULL",
                        Timestamp.from(Instant.now().plusSeconds(delay)), event.id());
                // Do not log HTTP bodies, destinations, tokens, or event payloads.
                log.warn("Outbox forwarding failed for event {} ({})", event.id(), failed.getClass().getSimpleName());
            }
        }
    }
}
