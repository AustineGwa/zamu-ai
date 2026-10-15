package ai.zamu.whatsapp;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import ai.zamu.conversations.Interactive;
import ai.zamu.shared.logging.Masking;

/**
 * Local development (h2 profile): nothing leaves the machine. Outbound messages appear in the inbox and in the
 * WhatsApp simulator at /dev/whatsapp; the console shows only that a send happened.
 */
@Component
@Profile("h2")
class ConsoleWhatsAppApi implements WhatsAppApi {

    private static final Logger log = LoggerFactory.getLogger(ConsoleWhatsAppApi.class);

    @Override
    public String sendText(Credentials credentials, String to, String text) {
        log.info("[local WhatsApp] text to {} ({} characters)", Masking.phone(to), text.length());
        return fakeId();
    }

    @Override
    public String sendTemplate(Credentials credentials, String to, String name, String language, List<String> params) {
        log.info("[local WhatsApp] template {} ({}) to {}", name, language, Masking.phone(to));
        return fakeId();
    }

    @Override
    public String sendInteractive(Credentials credentials, String to, String body, Interactive options) {
        log.info("[local WhatsApp] {} options to {}", options.choices().size(), Masking.phone(to));
        return fakeId();
    }

    @Override
    public void markReadWithTyping(Credentials credentials, String waMessageId) {
        // nothing to show locally
    }

    @Override
    public String submitTemplate(Credentials credentials, String name, String category, String language, String body,
            int parameterCount) {
        log.info("[local WhatsApp] template {} ({}) submitted for review", name, language);
        return "local-" + UUID.randomUUID();
    }

    private static String fakeId() {
        return "wamid.local." + UUID.randomUUID();
    }
}
