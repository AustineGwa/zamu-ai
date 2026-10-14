package ai.zamu.users;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Stored in the session between the email code and the authenticator code for Zamu team users (NFR-08). The user
 * is not authenticated until the second step passes.
 *
 * @param pendingSecret Base32 secret shown during first-time enrolment, null once enrolled
 */
record PendingSecondFactor(UUID userId, Instant expiresAt, String pendingSecret, int failures)
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    static final String SESSION_KEY = "zamu.login.pendingSecondFactor";

    boolean expired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    PendingSecondFactor withSecret(String secret) {
        return new PendingSecondFactor(userId, expiresAt, secret, failures);
    }

    PendingSecondFactor withFailure() {
        return new PendingSecondFactor(userId, expiresAt, pendingSecret, failures + 1);
    }
}
