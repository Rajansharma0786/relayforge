package dev.relayforge.delivery;

import java.net.URI;
import java.net.http.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:deliverytests;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "relayforge.internal-token=test-internal-token"
})
class DeliveryApiTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    final HttpClient http = HttpClient.newHttpClient();
    final String eventId = UUID.randomUUID().toString();
    String body() { return "{\"id\":\"" + eventId + "\",\"eventType\":\"invoice.paid\",\"destination\":\"https://example.com/hooks\",\"payload\":{\"amount\":100}}"; }
    @BeforeEach void clear() { jdbc.update("DELETE FROM delivery_jobs"); }

    HttpResponse<String> post(String body, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/internal/deliveries"))
                .header("Content-Type", "application/json");
        if (token != null) request.header("X-RelayForge-Token", token);
        return http.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test void internalIntakeRequiresTheConfiguredServiceToken() throws Exception {
        assertThat(post(body(), null).statusCode()).isEqualTo(401);
        assertThat(post(body(), "wrong").statusCode()).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM delivery_jobs", Integer.class)).isZero();
    }

    @Test void forwardedEventsArePersistedAndDeduplicated() throws Exception {
        assertThat(post(body(), "test-internal-token").statusCode()).isEqualTo(201);
        assertThat(post(body(), "test-internal-token").statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM delivery_jobs", Integer.class)).isEqualTo(1);
        var response = get("/api/deliveries/" + eventId);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("PENDING", eventId);
        assertThat(get("/api/deliveries").body()).contains(eventId);
    }

    @Test void aConflictingForwardIsRejected() throws Exception {
        post(body(), "test-internal-token");
        assertThat(post(body().replace("100", "200"), "test-internal-token").statusCode()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM delivery_jobs", Integer.class)).isEqualTo(1);
    }

    @Test void requestValidationAndLookupStatusesArePreserved() throws Exception {
        assertThat(post("{}", "test-internal-token").statusCode()).isEqualTo(400);
        assertThat(get("/api/deliveries/" + UUID.randomUUID()).statusCode()).isEqualTo(404);
        assertThat(get("/api/deliveries?limit=101").statusCode()).isEqualTo(400);
    }
}
