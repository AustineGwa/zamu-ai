package ai.zamu.conversations;

import java.util.UUID;

/**
 * A queued outbound message, for the channel that delivers it (the whatsapp send handler).
 *
 * @param windowOpen whether the 24-hour customer service window is open now (MSG-14)
 */
public record OutboundMessage(UUID id, UUID conversationId, Channel channel, String toPhone, MessageType type,
        String body, TemplateRef template, MessageStatus status, boolean windowOpen) {

    /** Job type the channel registers a handler for; payload {"messageId": "..."}. */
    public static final String SEND_JOB = "send_message";
}
