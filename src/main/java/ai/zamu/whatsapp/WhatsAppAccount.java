package ai.zamu.whatsapp;

import java.time.Instant;
import java.util.UUID;

/** A whatsapp_accounts row. The access token stays encrypted until the moment of a call. */
record WhatsAppAccount(UUID id, UUID tenantId, String wabaId, String phoneNumberId, String displayPhone,
        byte[] encryptedAccessToken, boolean webhookVerified, String qualityRating, Instant createdAt,
        Instant updatedAt) {

    WhatsAppAccountView toView() {
        return new WhatsAppAccountView(wabaId, phoneNumberId, displayPhone, webhookVerified, qualityRating);
    }
}
