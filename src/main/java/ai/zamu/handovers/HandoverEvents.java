package ai.zamu.handovers;

import java.util.UUID;

import ai.zamu.shared.ids.TenantId;

/**
 * Published by HandoverService; the conversations package listens and updates the conversation (status and
 * assignee), which it owns. Listeners run in the same transaction, so a missing conversation rolls everything back.
 */
public final class HandoverEvents {

    private HandoverEvents() {
    }

    /** A staff member took the chat (HO-05). */
    public record Accepted(TenantId tenantId, UUID conversationId, UUID userId) {
    }

    /** Staff handed the chat back to Zamu (HO-06). */
    public record Released(TenantId tenantId, UUID conversationId) {
    }

    /** Staff closed the chat (HO-06). */
    public record Closed(TenantId tenantId, UUID conversationId) {
    }
}
