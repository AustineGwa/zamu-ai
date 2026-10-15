package ai.zamu.whatsapp;

import java.util.List;

import ai.zamu.conversations.Interactive;

/**
 * The calls Zamu makes to the WhatsApp Cloud API. Returned strings are WhatsApp message IDs (wamid). Implemented
 * over HTTP by {@link RestClientWhatsAppApi}; the local h2 profile uses {@link ConsoleWhatsAppApi}.
 */
interface WhatsAppApi {

    String sendText(Credentials credentials, String to, String text);

    String sendTemplate(Credentials credentials, String to, String name, String language, List<String> params);

    /** A list message or reply buttons (BK-03); only inside the 24-hour window. */
    String sendInteractive(Credentials credentials, String to, String body, Interactive options);

    /** MSG-06: mark an inbound message read and show the typing indicator. */
    void markReadWithTyping(Credentials credentials, String waMessageId);

    /** Submits a template for Meta's review; returns Meta's template ID. */
    String submitTemplate(Credentials credentials, String name, String category, String language, String body,
            int parameterCount);
}
