package ai.zamu.users;

import java.time.Instant;
import java.util.UUID;

import ai.zamu.shared.security.ZamuRole;

/** A user as pages and other packages see it. Never carries the TOTP secret. */
public record UserView(UUID id, String email, String name, ZamuRole role, Instant lastLoginAt, Instant disabledAt,
        Instant createdAt) {

    public boolean active() {
        return disabledAt == null;
    }
}
