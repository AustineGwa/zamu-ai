package ai.zamu.whatsapp;

import java.util.List;

/**
 * The calls Zamu makes to the WhatsApp Cloud API. Returned strings are WhatsApp message IDs (wamid). Implemented
 * over HTTP by {@link RestClientWhatsAppApi}; the local h2 profile uses {@link ConsoleWhatsAppApi}.
 */
interface WhatsAppApi {

    String sendText(Credentials credentials, String to, String text);

    String sendTemplate(Credentials credentials, String to, String name, String language, List<String> params);

    /** MSG-06: mark an inbound message read and show the typing indicator. */
    void markReadWithTyping(Credentials credentials, String waMessageId);

    /** Submits a template for Meta's review; returns Meta's template ID. */
    String submitTemplate(Credentials credentials, String name, String category, String language, String body,
            int parameterCount);
}
