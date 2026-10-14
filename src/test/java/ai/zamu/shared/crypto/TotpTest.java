package ai.zamu.shared.crypto;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TotpTest {

    /** RFC 6238 appendix B test secret "12345678901234567890" (SHA-1). */
    private static final String RFC_SECRET = Totp.base32("12345678901234567890".getBytes(StandardCharsets.US_ASCII));

    @Test
    void matchesRfc6238TestVectors() {
        // RFC values are 8 digits; the last 6 digits are the 6-digit code.
        assertThat(Totp.codeAt(RFC_SECRET, Totp.step(Instant.ofEpochSecond(59)))).isEqualTo("287082");
        assertThat(Totp.codeAt(RFC_SECRET, Totp.step(Instant.ofEpochSecond(1111111109)))).isEqualTo("081804");
        assertThat(Totp.codeAt(RFC_SECRET, Totp.step(Instant.ofEpochSecond(1234567890)))).isEqualTo("005924");
        assertThat(Totp.codeAt(RFC_SECRET, Totp.step(Instant.ofEpochSecond(2000000000)))).isEqualTo("279037");
    }

    @Test
    void base32RoundTrips() {
        String secret = Totp.newSecret();
        assertThat(secret).hasSize(32).matches("[A-Z2-7]+");
        assertThat(Totp.base32(Totp.unbase32(secret))).isEqualTo(secret);
    }

    @Test
    void NFR_08_acceptsCurrentAndAdjacentStepOnly() {
        String secret = Totp.newSecret();
        Instant now = Instant.parse("2026-10-14T06:00:10Z");
        long step = Totp.step(now);
        assertThat(Totp.verify(secret, Totp.codeAt(secret, step), now, null)).hasValue(step);
        assertThat(Totp.verify(secret, Totp.codeAt(secret, step - 1), now, null)).hasValue(step - 1);
        assertThat(Totp.verify(secret, Totp.codeAt(secret, step + 1), now, null)).hasValue(step + 1);
        assertThat(Totp.verify(secret, Totp.codeAt(secret, step - 3), now, null)).isEmpty();
    }

    @Test
    void NFR_08_aUsedCodeCannotBeReplayed() {
        String secret = Totp.newSecret();
        Instant now = Instant.parse("2026-10-14T06:00:10Z");
        long step = Totp.step(now);
        assertThat(Totp.verify(secret, Totp.codeAt(secret, step), now, step)).isEmpty();
    }

    @Test
    void rejectsMalformedInput() {
        String secret = Totp.newSecret();
        Instant now = Instant.now();
        assertThat(Totp.verify(secret, null, now, null)).isEmpty();
        assertThat(Totp.verify(secret, "12345", now, null)).isEmpty();
        assertThat(Totp.verify(secret, "abcdef", now, null)).isEmpty();
    }

    @Test
    void otpauthUriIsWellFormed() {
        assertThat(Totp.otpauthUri("Zamu", "ops@zamu.co.ke", "ABCD"))
                .isEqualTo("otpauth://totp/Zamu:ops%40zamu.co.ke?secret=ABCD&issuer=Zamu&algorithm=SHA1&digits=6&period=30");
        assertThat(Totp.grouped("ABCDEFGHIJ")).isEqualTo("ABCD EFGH IJ");
    }
}
