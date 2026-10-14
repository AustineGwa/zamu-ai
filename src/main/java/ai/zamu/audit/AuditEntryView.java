package ai.zamu.audit;

import java.time.Instant;
import java.util.UUID;

/** Audit entry as shown in the dashboard. */
public record AuditEntryView(UUID id, UUID actorUserId, String action, String entity, UUID entityId, Instant createdAt) {
}
