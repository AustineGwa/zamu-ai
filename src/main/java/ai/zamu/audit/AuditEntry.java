package ai.zamu.audit;

import java.time.Instant;
import java.util.UUID;

/** A stored audit_logs row. */
record AuditEntry(UUID id, UUID tenantId, UUID actorUserId, String action, String entity, UUID entityId,
        String beforeJson, String afterJson, String ip, Instant createdAt) {
}
