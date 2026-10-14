package ai.zamu.conversations;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import ai.zamu.handovers.HandoverEvents;

/**
 * Applies handover decisions to the conversation, which this package owns. Runs synchronously in the publisher's
 * transaction: a conversation that isn't in the tenant throws, so the request becomes a 404 (NFR-09).
 */
@Component
class HandoverEventsListener {

    private final ConversationService conversations;

    HandoverEventsListener(ConversationService conversations) {
        this.conversations = conversations;
    }

    @EventListener
    void on(HandoverEvents.Accepted event) {
        conversations.applyStatus(event.tenantId(), event.conversationId(), ConversationStatus.HANDED_OVER, event.userId());
    }

    @EventListener
    void on(HandoverEvents.Released event) {
        conversations.applyStatus(event.tenantId(), event.conversationId(), ConversationStatus.BOT, null);
    }

    @EventListener
    void on(HandoverEvents.Closed event) {
        conversations.applyStatus(event.tenantId(), event.conversationId(), ConversationStatus.CLOSED, null);
    }
}
