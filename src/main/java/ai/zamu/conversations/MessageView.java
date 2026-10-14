package ai.zamu.conversations;

import java.time.Instant;
import java.util.UUID;

/** A message as the conversation page shows it. */
public record MessageView(UUID id, MessageDirection direction, MessageSender sender, String senderLabel,
        MessageType type, String body, String mediaKind, MessageStatus status, String errorCode, Instant createdAt) {

    public boolean outbound() {
        return direction == MessageDirection.OUT;
    }
}
