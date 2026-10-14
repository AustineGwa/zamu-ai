package ai.zamu.shared.jobs;

import java.util.UUID;

import ai.zamu.shared.ids.TenantId;

import tools.jackson.databind.ObjectMapper;

/**
 * A claimed job handed to a {@link JobHandler}. In a job, the tenant comes from here, never from the payload
 * (NFR-09).
 */
public record Job(UUID id, String type, TenantId tenantId, String payloadJson, int attempt, int maxAttempts,
        String lockKey) {

    public <T> T payload(ObjectMapper mapper, Class<T> type) {
        return mapper.readValue(payloadJson, type);
    }

    public boolean isLastAttempt() {
        return attempt >= maxAttempts;
    }
}
