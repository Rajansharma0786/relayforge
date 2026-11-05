package dev.relayforge.delivery.signing;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Version-one webhook signing; external delivery integration is a later milestone. */
public final class WebhookSignatures {
    private static final Pattern HEADER = Pattern.compile("t=(0|[1-9][0-9]{0,18}),v1=([0-9a-f]{64})");
    private WebhookSignatures() {}

    public static String sign(byte[] secret, byte[] body, long timestamp) {
        validate(secret, body);
        if (timestamp < 0) throw new IllegalArgumentException("Timestamp must be nonnegative.");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            mac.update((timestamp + ".").getBytes(StandardCharsets.US_ASCII));
            return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(mac.doFinal(body));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("HmacSHA256 is unavailable.", error);
        }
    }

    public static boolean verify(byte[] secret, byte[] body, String header, Instant now, Duration tolerance) {
        validate(secret, body);
        if (now == null || tolerance == null || tolerance.isNegative()) {
            throw new IllegalArgumentException("Provide a current time and nonnegative tolerance.");
        }
        if (header == null || header.length() > 100) return false;
        var match = HEADER.matcher(header);
        if (!match.matches()) return false;
        try {
            long timestamp = Long.parseLong(match.group(1));
            if (Duration.between(Instant.ofEpochSecond(timestamp), now).abs().compareTo(tolerance) > 0) return false;
            String expected = sign(secret, body, timestamp).split("v1=", 2)[1];
            return MessageDigest.isEqual(HexFormat.of().parseHex(expected), HexFormat.of().parseHex(match.group(2)));
        } catch (java.time.DateTimeException | NumberFormatException invalid) {
            return false;
        }
    }

    private static void validate(byte[] secret, byte[] body) {
        if (secret == null || secret.length == 0 || body == null) {
            throw new IllegalArgumentException("Provide a nonempty secret and exact body bytes.");
        }
    }
}
