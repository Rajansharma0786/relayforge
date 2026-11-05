package dev.relayforge.delivery.signing;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WebhookSignaturesTest {
    final byte[] secret = "test-secret".getBytes(StandardCharsets.UTF_8);
    final byte[] body = "{\"event\":\"invoice.paid\"}".getBytes(StandardCharsets.UTF_8);
    final long timestamp = 1700000000;
    final Instant now = Instant.ofEpochSecond(timestamp);
    final Duration window = Duration.ofMinutes(5);
    final String reference = "t=1700000000,v1=f6f4b00b62e5d72c8059b70a555be877bdae3c7cb9fd766d3334e791802bb995";

    @Test void matchesTheIndependentOpenSslVector() {
        assertThat(WebhookSignatures.sign(secret, body, timestamp)).isEqualTo(reference);
        assertThat(WebhookSignatures.verify(secret, body, reference, now, window)).isTrue();
    }
    @Test void rejectsBodySecretAndTimestampTampering() {
        assertThat(WebhookSignatures.verify(secret, new byte[0], reference, now, window)).isFalse();
        assertThat(WebhookSignatures.verify("wrong".getBytes(), body, reference, now, window)).isFalse();
        assertThat(WebhookSignatures.verify(secret, body, reference.replace("1700000000", "1700000001"), now, window)).isFalse();
    }
    @Test void enforcesBothFreshnessBoundaries() {
        for (long offset : new long[]{-300, 300})
            assertThat(WebhookSignatures.verify(secret, body, reference, now.plusSeconds(offset), window)).isTrue();
        for (long offset : new long[]{-301, 301})
            assertThat(WebhookSignatures.verify(secret, body, reference, now.plusSeconds(offset), window)).isFalse();
    }
    @Test void rejectsAmbiguousOrMalformedHeaders() {
        for (String header : new String[]{null, "", reference + "\n", reference + ",t=1",
                reference.replace("v1", "v2"), reference.replace("t=170", "t=0170"),
                "t=9999999999999999999,v1=" + "0".repeat(64), "x".repeat(1000)})
            assertThat(WebhookSignatures.verify(secret, body, header, now, window)).isFalse();
    }
    @Test void rejectsInvalidSigningConfiguration() {
        assertThatThrownBy(() -> WebhookSignatures.sign(new byte[0], body, timestamp)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WebhookSignatures.sign(secret, null, timestamp)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WebhookSignatures.sign(secret, body, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WebhookSignatures.verify(secret, body, reference, now, Duration.ofSeconds(-1))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void supportsEmptyBodiesAndRequiresReceiverReplayDeduplication() {
        var header = WebhookSignatures.sign(secret, new byte[0], timestamp);
        assertThat(WebhookSignatures.verify(secret, new byte[0], header, now, Duration.ZERO)).isTrue();
        assertThat(WebhookSignatures.verify(secret, new byte[0], header, now, Duration.ZERO)).isTrue();
    }
}
