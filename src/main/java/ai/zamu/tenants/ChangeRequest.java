package ai.zamu.tenants;

import java.time.Instant;
import java.util.UUID;

/**
 * An owner's request to the Zamu team (spec: Change requests screen; knowledge base "suggest a change"; REP-02
 * "suggest KB entry").
 *
 * @param status open, done or declined
 */
public record ChangeRequest(UUID id, UUID tenantId, String tenantName, UUID requestedBy, ChangeRequestType type,
        String details, String status, UUID handledBy, Instant handledAt, String note, Instant createdAt) {

    public boolean open() {
        return "open".equals(status);
    }
}
