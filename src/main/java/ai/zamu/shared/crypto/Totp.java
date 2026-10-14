package ai.zamu.shared.crypto;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.OptionalLong;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Time-based one-time passwords (RFC 6238, HMAC-SHA1, 6 digits, 30-second steps), compatible with
 * Google Authenticator, Microsoft Authenticator, 1Password and similar apps. Second factor for Zamu team
 * operators and admins (NFR-08).
 */
public final class Totp {

    public static final int DIGITS = 6;
    public static final long STEP_SECONDS = 30;
    /** Accept one step either side of now, to allow for phone clock drift. */
    private static final int WINDOW = 1;
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {
    }

    /** A new 160-bit secret, Base32-encoded as authenticator apps expect. */
    public static String newSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return base32(bytes);
    }

    public static long step(Instant now) {
        return Math.floorDiv(now.getEpochSecond(), STEP_SECONDS);
    }

    /** The code for a given step; exposed for tests and for showing nothing else. */
    public static String codeAt(String base32Secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(unbase32(base32Secret), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % 1_000_000;
            return String.format("%06d", otp);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("TOTP failed", e);
        }
    }

    /**
     * Checks a code. Returns the matching step, which must be stored and passed back as
     * {@code lastUsedStep} next time so a code can't be replayed. Empty if the code is wrong or reused.
     */
    public static OptionalLong verify(String base32Secret, String code, Instant now, Long lastUsedStep) {
        if (code == null) {
            return OptionalLong.empty();
        }
        String digits = code.replaceAll("\\s", "");
        if (!digits.matches("\\d{" + DIGITS + "}")) {
            return OptionalLong.empty();
        }
        long current = step(now);
        for (long s = current - WINDOW; s <= current + WINDOW; s++) {
            if (lastUsedStep != null && s <= lastUsedStep) {
                continue;
            }
            if (java.security.MessageDigest.isEqual(
                    codeAt(base32Secret, s).getBytes(StandardCharsets.US_ASCII),
                    digits.getBytes(StandardCharsets.US_ASCII))) {
                return OptionalLong.of(s);
            }
        }
        return OptionalLong.empty();
    }

    /** otpauth:// URI that authenticator apps accept (typed in, or as a QR code). */
    public static String otpauthUri(String issuer, String account, String base32Secret) {
        String label = enc(issuer) + ":" + enc(account);
        return "otpauth://totp/" + label + "?secret=" + base32Secret + "&issuer=" + enc(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    /** Groups a secret in blocks of four so people can type it into an app. */
    public static String grouped(String base32Secret) {
        return base32Secret.replaceAll("(.{4})(?!$)", "$1 ");
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    static String base32(byte[] data) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32.charAt((buffer >> (bits - 5)) & 0x1F));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(BASE32.charAt((buffer << (5 - bits)) & 0x1F));
        }
        return out.toString();
    }

    static byte[] unbase32(String encoded) {
        String clean = encoded.replaceAll("[\\s=]", "").toUpperCase();
        ByteBuffer out = ByteBuffer.allocate(clean.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char c : clean.toCharArray()) {
            int value = BASE32.indexOf(c);
            if (value < 0) {
                throw new IllegalArgumentException("Invalid Base32 character");
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                out.put((byte) ((buffer >> (bits - 8)) & 0xFF));
                bits -= 8;
            }
        }
        return out.array();
    }
}
