package ai.zamu.conversations;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import ai.zamu.shared.phone.KenyanPhone;

/** Everything the conversation page needs. */
public record ConversationDetail(UUID id, UUID customerId, String customerName, String phone, String language,
        boolean blocked, boolean optedOut, Channel channel, ConversationStatus status, UUID assignedUserId,
        boolean windowOpen, Instant windowExpiresAt, List<MessageView> messages) {

    public String displayName() {
        return customerName == null || customerName.isBlank() ? phoneDisplay() : customerName;
    }

    public String phoneDisplay() {
        return phone != null && phone.startsWith("254") ? KenyanPhone.display(phone) : "+" + phone;
    }
}
