package dev.relayforge.events;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:eventtests;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "relayforge.outbox.enabled=false"
})
class EventApiTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;
    @Autowired EventService events;
    @Autowired TransactionTemplate transactions;
    final HttpClient http = HttpClient.newHttpClient();
    static final String BODY = """
            {"eventType":"invoice.paid","destination":"https://example.com/hooks","payload":{"amount":100,"nested":{"b":2,"a":1}}}
            """;

    @BeforeEach void clear() { jdbc.update("DELETE FROM event_outbox"); jdbc.update("DELETE FROM events"); }

    HttpResponse<String> post(String body, String key) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/events"))
                .header("Content-Type", "application/json");
        if (key != null) request.header("Idempotency-Key", key);
        return http.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
    String id(HttpResponse<String> response) { return json.readTree(response.body()).get("id").stringValue(); }

    @Test void acceptancePersistsEventAndOutboxTogether() throws Exception {
        var response = post(BODY, "invoice-1");
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers().firstValue("Location")).contains("/api/events/" + id(response));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM events", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox", Integer.class)).isEqualTo(1);
        assertThat(get("/api/events/" + id(response)).statusCode()).isEqualTo(200);
    }

    @Test void replayCanonicalizesNestedObjectKeys() throws Exception {
        var first = post(BODY, "same");
        var second = post(BODY.replace("\"b\":2,\"a\":1", "\"a\":1,\"b\":2"), "same");
        assertThat(second.statusCode()).isEqualTo(200);
        assertThat(id(second)).isEqualTo(id(first));
        assertThat(second.headers().firstValue("Idempotency-Replayed")).contains("true");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox", Integer.class)).isEqualTo(1);
    }

    @Test void conflictingReplayDoesNotCreateAnEvent() throws Exception {
        post(BODY, "same");
        var response = post(BODY.replace("100", "200"), "same");
        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.body()).contains("Idempotency key");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM events", Integer.class)).isEqualTo(1);
    }

    @Test void concurrentRetriesHaveOneWinningInsert() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<HttpResponse<String>>> futures = new ArrayList<>();
            for (int i = 0; i < 12; i++) futures.add(executor.submit(() -> post(BODY, "concurrent")));
            Set<String> ids = new HashSet<>();
            int created = 0;
            for (var future : futures) {
                var response = future.get(10, TimeUnit.SECONDS);
                assertThat(response.statusCode()).isIn(200, 201);
                if (response.statusCode() == 201) created++;
                ids.add(id(response));
            }
            assertThat(created).isEqualTo(1);
            assertThat(ids).hasSize(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox", Integer.class)).isEqualTo(1);
        }
    }

    @Test void validatesRequestShapeDestinationAndKey() throws Exception {
        for (String invalid : List.of(BODY.replace("https://example.com/hooks", "ftp://example.com/a"),
                BODY.replace("https://example.com/hooks", "https://user:pass@example.com"),
                BODY.replace("invoice.paid", "bad type"), "{\"eventType\":\"test\"}", "{")) {
            assertThat(post(invalid, "key").statusCode()).isEqualTo(400);
        }
        assertThat(post(BODY, " ").statusCode()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM events", Integer.class)).isZero();
    }

    @Test void missingEventsAndInvalidLimitsHaveUsefulStatuses() throws Exception {
        assertThat(get("/api/events/" + UUID.randomUUID()).statusCode()).isEqualTo(404);
        assertThat(get("/api/events?limit=0").statusCode()).isEqualTo(400);
        assertThat(get("/api/events?limit=101").statusCode()).isEqualTo(400);
    }

    @Test void anOutboxFailureRollsBackTheEventInsert() {
        jdbc.execute("ALTER TABLE event_outbox ADD CONSTRAINT reject_test CHECK(attempts > 0)");
        try {
            assertThatThrownBy(() -> events.accept(new CreateEvent("test.event", "https://example.com", Map.of()), "rollback"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM events", Integer.class)).isZero();
        } finally { jdbc.execute("ALTER TABLE event_outbox DROP CONSTRAINT reject_test"); }
    }

    @Test void outboxRecoversFromServiceFailureWithoutLosingTheEvent() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var calls = new AtomicInteger();
        var token = new AtomicReference<String>();
        var forwardedBody = new AtomicReference<String>();
        server.createContext("/internal/deliveries", exchange -> {
            token.set(exchange.getRequestHeaders().getFirst("X-RelayForge-Token"));
            forwardedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            int status = calls.incrementAndGet() == 1 ? 503 : 201;
            exchange.sendResponseHeaders(status, -1); exchange.close();
        });
        server.start();
        try {
            var accepted = post(BODY, "recover");
            var dispatcher = new OutboxDispatcher(jdbc, events, transactions, RestClient.builder(),
                    "http://127.0.0.1:" + server.getAddress().getPort(), "test-token");
            dispatcher.forwardDueEvents();
            assertThat(jdbc.queryForObject("SELECT status FROM events", String.class)).isEqualTo("ACCEPTED");
            assertThat(jdbc.queryForObject("SELECT attempts FROM event_outbox", Integer.class)).isEqualTo(1);
            dispatcher.forwardDueEvents();
            assertThat(calls.get()).isEqualTo(1);
            jdbc.update("UPDATE event_outbox SET next_attempt_at=?", Timestamp.from(Instant.now().minusSeconds(1)));
            dispatcher.forwardDueEvents();
            assertThat(calls.get()).isEqualTo(2);
            assertThat(token.get()).isEqualTo("test-token");
            assertThat(forwardedBody.get()).contains(id(accepted));
            assertThat(jdbc.queryForObject("SELECT status FROM events", String.class)).isEqualTo("FORWARDED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_outbox WHERE forwarded_at IS NOT NULL", Integer.class)).isEqualTo(1);
        } finally { server.stop(0); }
    }
}
