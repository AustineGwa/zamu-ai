package ai.zamu.users;

import java.time.Instant;
import java.util.UUID;

/** A login_codes row. Only the HMAC of the code is stored. */
record LoginCode(UUID id, String email, byte[] codeHash, Instant expiresAt, int attempts, Instant consumedAt,
        Instant createdAt) {
}
