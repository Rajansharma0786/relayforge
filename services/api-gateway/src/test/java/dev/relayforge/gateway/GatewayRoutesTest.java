package dev.relayforge.gateway;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutesTest {
    static final HttpServer upstream;
    static {
        try {
            upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            upstream.createContext("/", exchange -> {
                byte[] body = (exchange.getRequestURI().getPath() + "|" +
                        exchange.getRequestHeaders().getFirst("Idempotency-Key") + "|" +
                        new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "text/plain");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body); exchange.close();
            });
            upstream.start();
        } catch (Exception error) { throw new ExceptionInInitializerError(error); }
    }
    @DynamicPropertySource static void upstream(DynamicPropertyRegistry registry) {
        registry.add("relayforge.events-url", () -> "http://127.0.0.1:" + upstream.getAddress().getPort());
        registry.add("relayforge.delivery-url", () -> "http://127.0.0.1:" + upstream.getAddress().getPort());
    }
    @AfterAll static void stop() { upstream.stop(0); }
    @LocalServerPort int port;
    final HttpClient http = HttpClient.newHttpClient();

    @Test void forwardsEventPayloadAndIdempotencyHeaders() throws Exception {
        var response = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/events"))
                .header("Content-Type", "application/json").header("Idempotency-Key", "same-key")
                .POST(HttpRequest.BodyPublishers.ofString("{\"test\":true}")).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("/api/events|same-key|{\"test\":true}");
    }
    @Test void routesDeliveryQueries() throws Exception {
        var response = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/deliveries"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).startsWith("/api/deliveries|");
    }
    @Test void doesNotExposeInternalDeliveryIntake() throws Exception {
        var response = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/internal/deliveries"))
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(404);
    }
}
