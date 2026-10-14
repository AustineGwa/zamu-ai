package ai.zamu.whatsapp;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * MSG-01: Meta signs each webhook body with the app secret, HMAC-SHA256, in the {@code X-Hub-Signature-256}
 * header as {@code sha256=<hex>}. Verified over the exact raw bytes, compared in constant time.
 */
final class WebhookSignature {

    static final String HEADER = "X-Hub-Signature-256";
    private static final String PREFIX = "sha256=";

    private WebhookSignature() {
    }

    static boolean valid(String appSecret, byte[] body, String header) {
        if (appSecret == null || appSecret.isBlank() || header == null || !header.startsWith(PREFIX) || body == null) {
            return false;
        }
        byte[] given;
        try {
            given = HexFormat.of().parseHex(header.substring(PREFIX.length()).trim().toLowerCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return false;
        }
        return MessageDigest.isEqual(sign(appSecret, body), given);
    }

    static byte[] sign(String appSecret, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(body);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    static String header(String appSecret, byte[] body) {
        return PREFIX + HexFormat.of().formatHex(sign(appSecret, body));
    }
}
