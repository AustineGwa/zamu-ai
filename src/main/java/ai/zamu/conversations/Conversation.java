package ai.zamu.conversations;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** A conversations row. */
record Conversation(UUID id, UUID tenantId, UUID customerId, Channel channel, ConversationStatus status,
        UUID assignedUserId, Instant openedAt, Instant lastMessageAt, Instant lastCustomerMessageAt,
        Instant windowExpiresAt, Instant billableSessionStart, int unreadCount, boolean holdingReplySent,
        Instant closedAt, Instant createdAt, Instant updatedAt) {

    /** WhatsApp's customer service window (MSG-14). */
    static final Duration WINDOW = Duration.ofHours(24);

    /** Free-form messages may be sent only inside the 24-hour window (MSG-14, HO-07). */
    boolean windowOpen(Instant now) {
        return windowExpiresAt != null && now.isBefore(windowExpiresAt);
    }

    /** MSG-04: a new conversation starts when none is open or the customer's last message is over 24 hours old. */
    boolean continuesAt(Instant now) {
        return status != ConversationStatus.CLOSED
                && lastCustomerMessageAt != null
                && Duration.between(lastCustomerMessageAt, now).compareTo(WINDOW) <= 0;
    }

    /** MSG-17: a billable conversation is counted once per customer per 24 hours from their first message. */
    boolean billablePeriodExpired(Instant now) {
        return billableSessionStart == null || !now.isBefore(billableSessionStart.plus(WINDOW));
    }
}
