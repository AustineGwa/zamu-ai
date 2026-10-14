package ai.zamu.conversations;

import java.time.Instant;
import java.util.Objects;

import ai.zamu.shared.ids.TenantId;

/**
 * A customer message as any channel delivers it to the engine. WhatsApp builds it from the webhook; voice will
 * build it from speech-to-text.
 *
 * @param phone       the customer's number in international digits, e.g. 254712345678
 * @param externalId  the channel's message ID (WhatsApp wamid), used to drop duplicate deliveries (MSG-02)
 * @param mediaRef    for non-text messages: kind and provider media ID, e.g. "image:1234" (MSG-13)
 */
public record InboundMessage(Channel channel, TenantId tenantId, String phone, String profileName, String externalId,
        MessageType type, String text, String mediaRef, Instant receivedAt) {

    public InboundMessage {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(phone, "phone");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(receivedAt, "receivedAt");
    }
}
