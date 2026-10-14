package ai.zamu.shared.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Encrypts secrets at rest (NFR-06): WhatsApp tokens, Daraja keys, calendar refresh tokens, TOTP secrets.
 * AES-256-GCM with a key held outside the database (ZAMU_ENCRYPTION_KEY).
 *
 * <p>Stored format: {@code [version=1][12-byte IV][ciphertext + 16-byte tag]}, written to VARBINARY columns.
 * The version byte leaves room for key rotation.
 */
@Component
public class SecretCipher {

    private static final byte VERSION = 1;
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(@Value("${zamu.security.encryption-key}") String base64Key) {
        this.key = new SecretKeySpec(Keys.decode32(base64Key, "ZAMU_ENCRYPTION_KEY"), "AES");
    }

    public byte[] encrypt(byte[] plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);
            byte[] out = new byte[1 + IV_LENGTH + ciphertext.length];
            out[0] = VERSION;
            System.arraycopy(iv, 0, out, 1, IV_LENGTH);
            System.arraycopy(ciphertext, 0, out, 1 + IV_LENGTH, ciphertext.length);
            return out;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public byte[] decrypt(byte[] stored) {
        if (stored == null || stored.length < 1 + IV_LENGTH + TAG_BITS / 8 || stored[0] != VERSION) {
            throw new IllegalArgumentException("Not a Zamu encrypted value");
        }
        try {
            byte[] iv = Arrays.copyOfRange(stored, 1, 1 + IV_LENGTH);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return cipher.doFinal(stored, 1 + IV_LENGTH, stored.length - 1 - IV_LENGTH);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Decryption failed", e);
        }
    }

    public byte[] encryptString(String plaintext) {
        return encrypt(plaintext.getBytes(StandardCharsets.UTF_8));
    }

    public String decryptString(byte[] stored) {
        return new String(decrypt(stored), StandardCharsets.UTF_8);
    }

    static final class Keys {
        private Keys() {
        }

        static byte[] decode32(String base64, String name) {
            if (base64 == null || base64.isBlank()) {
                throw new IllegalStateException(name + " is not set. Generate one with: openssl rand -base64 32");
            }
            byte[] bytes = Base64.getDecoder().decode(base64.trim());
            if (bytes.length != 32) {
                throw new IllegalStateException(name + " must be 32 bytes (Base64), was " + bytes.length);
            }
            return bytes;
        }
    }
}
