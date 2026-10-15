package ai.zamu.audit;

import java.time.Instant;
import java.util.UUID;

/** One row of the admin audit log search (NFR-14), with the tenant's and actor's names. */
public record AuditSearchRow(UUID id, UUID tenantId, String tenant, String actor, String action, String entity,
        UUID entityId, String after, String ip, Instant createdAt) {
}
