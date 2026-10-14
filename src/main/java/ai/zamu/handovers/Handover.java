package ai.zamu.handovers;

import java.time.Instant;
import java.util.UUID;

/** A handovers row. Open while resolved_at is null. */
record Handover(UUID id, UUID tenantId, UUID conversationId, HandoverReason reason, Instant requestedAt,
        UUID acceptedBy, Instant acceptedAt, Instant firstStaffReplyAt, Instant escalatedAt, Instant resolvedAt) {

    HandoverView toView() {
        return new HandoverView(id, conversationId, reason, requestedAt, acceptedBy, acceptedAt);
    }
}
