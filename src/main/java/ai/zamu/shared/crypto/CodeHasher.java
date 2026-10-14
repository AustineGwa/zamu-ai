package ai.zamu.shared.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Hashes short one-time codes with HMAC-SHA256 and a server-side key (ZAMU_CODE_HASH_KEY).
 * A plain hash of a 6-digit code could be reversed by trying all million values; the key prevents that
 * if the database leaks.
 */
@Component
public class CodeHasher {

    private final SecretKeySpec key;

    public CodeHasher(@Value("${zamu.security.code-hash-key}") String base64Key) {
        this.key = new SecretKeySpec(SecretCipher.Keys.decode32(base64Key, "ZAMU_CODE_HASH_KEY"), "HmacSHA256");
    }

    public byte[] hash(String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal(code.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC failed", e);
        }
    }

    /** Constant-time comparison. */
    public boolean matches(String code, byte[] expectedHash) {
        return expectedHash != null && MessageDigest.isEqual(hash(code), expectedHash);
    }
}
