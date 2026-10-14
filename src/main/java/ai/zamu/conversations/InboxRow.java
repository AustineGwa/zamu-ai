package ai.zamu.conversations;

import java.time.Instant;
import java.util.UUID;

import ai.zamu.shared.phone.KenyanPhone;

/** One line of the inbox list. */
public record InboxRow(UUID id, UUID customerId, String customerName, String phone, Channel channel,
        ConversationStatus status, UUID assignedUserId, Instant lastMessageAt, int unread) {

    public String displayName() {
        return customerName == null || customerName.isBlank() ? phoneDisplay() : customerName;
    }

    public String phoneDisplay() {
        return phone != null && phone.startsWith("254") ? KenyanPhone.display(phone) : "+" + phone;
    }

    public boolean needsAttention() {
        return status == ConversationStatus.HANDED_OVER && assignedUserId == null;
    }
}
