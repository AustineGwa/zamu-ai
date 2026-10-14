package ai.zamu.conversations;

import java.time.Instant;
import java.util.UUID;

/** A messages row. */
record Message(UUID id, UUID tenantId, UUID conversationId, MessageDirection direction, MessageSender sender,
        UUID senderUserId, String waMessageId, MessageType type, String body, String mediaRef, String templateJson,
        MessageStatus status, String errorCode, Instant createdAt, Instant updatedAt) {
}
