package ai.zamu.shared.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretCipherTest {

    private static final String KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private final SecretCipher cipher = new SecretCipher(KEY);

    @Test
    void NFR_06_roundTripsAndNeverStoresPlaintext() {
        byte[] stored = cipher.encryptString("EAAG-whatsapp-token");
        assertThat(new String(stored, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("whatsapp");
        assertThat(cipher.decryptString(stored)).isEqualTo("EAAG-whatsapp-token");
    }

    @Test
    void sameSecretEncryptsDifferentlyEachTime() {
        assertThat(cipher.encryptString("x")).isNotEqualTo(cipher.encryptString("x"));
    }

    @Test
    void tamperedValueIsRejected() {
        byte[] stored = cipher.encryptString("secret");
        stored[stored.length - 1] ^= 1;
        assertThatThrownBy(() -> cipher.decrypt(stored)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void missingOrShortKeyFailsAtStartupWithAClearMessage() {
        assertThatThrownBy(() -> new SecretCipher("")).hasMessageContaining("ZAMU_ENCRYPTION_KEY is not set");
        assertThatThrownBy(() -> new SecretCipher("c2hvcnQ=")).hasMessageContaining("must be 32 bytes");
    }
}
