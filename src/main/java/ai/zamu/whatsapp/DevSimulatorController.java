package ai.zamu.whatsapp;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import ai.zamu.conversations.ConversationService;
import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.phone.KenyanPhone;

import tools.jackson.databind.ObjectMapper;

/**
 * Local development only (h2 profile): pretend to be a customer on WhatsApp. Builds the same webhook body Meta
 * sends and passes it through the real webhook service, so routing, de-duplication, jobs and the engine all run.
 * Zamu's replies appear in the inbox (the local WhatsApp client only logs that a send happened).
 */
@Controller
@Profile("h2")
@RequestMapping("/dev/whatsapp")
class DevSimulatorController {

    private final WhatsAppAccountService accounts;
    private final ConversationService conversations;
    private final WhatsAppWebhookService webhooks;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    DevSimulatorController(WhatsAppAccountService accounts, ConversationService conversations,
            WhatsAppWebhookService webhooks, ObjectMapper objectMapper, Clock clock) {
        this.accounts = accounts;
        this.conversations = conversations;
        this.webhooks = webhooks;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @GetMapping
    String page(TenantId tenantId, @RequestParam(name = "as", required = false) String as, Model model) {
        model.addAttribute("account", accounts.view(tenantId).orElse(null));
        Object phone = as != null ? as : model.getAttribute("phone");
        if (as != null && !model.containsAttribute("phone")) {
            model.addAttribute("phone", as);
        }
        String from = phone == null ? null : KenyanPhone.normalise(phone.toString()).orElse(null);
        model.addAttribute("options", from == null ? null : conversations.latestOptions(tenantId, from).orElse(null));
        return "whatsapp/dev-simulator";
    }

    @PostMapping
    String send(TenantId tenantId, @RequestParam String phone, @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "text") String kind, @RequestParam(required = false) String text,
            @RequestParam(required = false) String replyId, RedirectAttributes redirect) {
        WhatsAppAccountView account = accounts.view(tenantId).orElse(null);
        String from = KenyanPhone.normalise(phone).orElse(null);
        redirect.addFlashAttribute("phone", phone);
        redirect.addFlashAttribute("name", name);
        if (account == null) {
            redirect.addFlashAttribute("error", "Connect WhatsApp for this business first.");
            return "redirect:/dev/whatsapp";
        }
        if (from == null) {
            redirect.addFlashAttribute("error", "Enter a Kenyan mobile number.");
            return "redirect:/dev/whatsapp";
        }
        if ("text".equals(kind) && (text == null || text.isBlank())) {
            redirect.addFlashAttribute("error", "Type a message.");
            return "redirect:/dev/whatsapp";
        }
        webhooks.receive(objectMapper.writeValueAsBytes(body(account, from, name, kind, text, replyId)));
        redirect.addFlashAttribute("success", "Sent as the customer. Zamu's reply appears in the inbox in a moment.");
        redirect.addAttribute("as", from);
        return "redirect:/dev/whatsapp";
    }

    private Map<String, Object> body(WhatsAppAccountView account, String from, String name, String kind, String text,
            String replyId) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("from", from);
        message.put("id", "wamid.dev." + UUID.randomUUID());
        message.put("timestamp", String.valueOf(clock.instant().getEpochSecond()));
        if ("reply".equals(kind) && replyId != null) { // a tapped list row, as Meta sends it
            message.put("type", "interactive");
            message.put("interactive", Map.of("type", "list_reply",
                    "list_reply", Map.of("id", replyId, "title", text == null ? "" : text)));
        } else if ("image".equals(kind)) {
            message.put("type", "image");
            message.put("image", Map.of("id", "dev-media-" + UUID.randomUUID(), "mime_type", "image/jpeg"));
        } else {
            message.put("type", "text");
            message.put("text", Map.of("body", text.trim()));
        }
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("messaging_product", "whatsapp");
        value.put("metadata", Map.of("display_phone_number", account.displayPhone(),
                "phone_number_id", account.phoneNumberId()));
        value.put("contacts", List.of(Map.of("wa_id", from,
                "profile", Map.of("name", name == null || name.isBlank() ? "" : name.trim()))));
        value.put("messages", List.of(message));
        return Map.of("object", "whatsapp_business_account",
                "entry", List.of(Map.of("id", account.wabaId(),
                        "changes", List.of(Map.of("field", "messages", "value", value)))));
    }
}
