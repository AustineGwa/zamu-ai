package ai.zamu.shared.jobs;

import java.time.Instant;
import java.util.Objects;

import ai.zamu.shared.ids.TenantId;

/**
 * A job to enqueue.
 *
 * @param type     job type, e.g. {@code process_inbound_message}; a {@link JobHandler} must exist for it
 * @param tenantId the tenant the work belongs to; null only for system-wide jobs
 * @param payload  serialised to JSON. Keep personal data out unless the job truly needs it (NFR-13)
 * @param runAt    earliest time to run; null means now
 * @param lockKey  jobs sharing a lock key run one at a time, in order (MSG-05); usually a conversation ID
 */
public record NewJob(String type, TenantId tenantId, Object payload, Instant runAt, String lockKey, int maxAttempts) {

    public static final int DEFAULT_MAX_ATTEMPTS = 5;

    public NewJob {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(payload, "payload");
        if (lockKey != null && lockKey.length() > 64) {
            throw new IllegalArgumentException("lockKey longer than 64 characters");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
    }

    public static NewJob of(String type, TenantId tenantId, Object payload) {
        return new NewJob(type, tenantId, payload, null, null, DEFAULT_MAX_ATTEMPTS);
    }

    public NewJob runAt(Instant when) {
        return new NewJob(type, tenantId, payload, when, lockKey, maxAttempts);
    }

    public NewJob lockKey(String key) {
        return new NewJob(type, tenantId, payload, runAt, key, maxAttempts);
    }

    public NewJob maxAttempts(int attempts) {
        return new NewJob(type, tenantId, payload, runAt, lockKey, attempts);
    }
}
