package ai.zamu.handovers;

import java.time.Instant;
import java.util.UUID;

/** An open handover as the inbox shows it. */
public record HandoverView(UUID id, UUID conversationId, HandoverReason reason, Instant requestedAt, UUID acceptedBy,
        Instant acceptedAt) {

    public boolean accepted() {
        return acceptedBy != null;
    }
}
