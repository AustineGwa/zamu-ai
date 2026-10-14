package ai.zamu.whatsapp;

/** The connection as pages show it. Never includes the access token (NFR-06). */
public record WhatsAppAccountView(String wabaId, String phoneNumberId, String displayPhone, boolean webhookVerified,
        String qualityRating) {
}
