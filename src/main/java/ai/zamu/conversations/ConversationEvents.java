package ai.zamu.conversations;

import java.time.Instant;

import ai.zamu.shared.ids.TenantId;

/** Events for other packages (reports listens for usage metering, MSG-17). */
public final class ConversationEvents {

    private ConversationEvents() {
    }

    /** MSG-17: a billable conversation started (once per customer per 24 hours). */
    public record BillableConversationStarted(TenantId tenantId, Instant at) {
    }

    /** A template message was queued: Meta bills these per message (REP-03 WhatsApp billable messages). */
    public record TemplateQueued(TenantId tenantId, Instant at) {
    }

    /** A message was stored, inbound or outbound. */
    public record MessageRecorded(TenantId tenantId, MessageDirection direction, Instant at) {
    }
}
