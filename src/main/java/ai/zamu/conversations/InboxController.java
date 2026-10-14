package ai.zamu.conversations;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import ai.zamu.handovers.HandoverService;
import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.security.ZamuPrincipal;
import ai.zamu.shared.web.LiveUpdates;

/**
 * Staff inbox (spec: Inbox and Conversation screens). Routes: GET /app/inbox (+ /app/inbox/list fragment),
 * GET /app/conversations/{id} (+ /messages fragment), POST /app/conversations/{id}/messages, GET /app/stream,
 * POST /app/customers/{id}/block. The tenant always comes from the session (NFR-09).
 */
@Controller
class InboxController {

    private final ConversationService conversations;
    private final HandoverService handovers;
    private final LiveUpdates live;

    InboxController(ConversationService conversations, HandoverService handovers, LiveUpdates live) {
        this.conversations = conversations;
        this.handovers = handovers;
        this.live = live;
    }

    @GetMapping("/app/inbox")
    String inbox(TenantId tenantId, @RequestParam(required = false) String status,
            @RequestParam(required = false) String q, Model model) {
        addList(tenantId, status, q, model);
        model.addAttribute("filters", InboxFilter.values());
        model.addAttribute("openHandovers", handovers.openCount(tenantId));
        return "conversations/inbox";
    }

    /** The list alone, for live refresh (htmx, on the "inbox" server-sent event). */
    @GetMapping("/app/inbox/list")
    String inboxList(TenantId tenantId, @RequestParam(required = false) String status,
            @RequestParam(required = false) String q, Model model) {
        addList(tenantId, status, q, model);
        return "conversations/inbox :: list";
    }

    @GetMapping("/app/conversations/{conversationId}")
    String conversation(TenantId tenantId, @PathVariable UUID conversationId, Model model) {
        conversations.markRead(tenantId, conversationId);
        addConversation(tenantId, conversationId, model);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", StaffReplyForm.empty());
        }
        return "conversations/conversation";
    }

    /** The messages alone, for live refresh. */
    @GetMapping("/app/conversations/{conversationId}/messages")
    String messages(TenantId tenantId, @PathVariable UUID conversationId, Model model) {
        model.addAttribute("conversation", conversations.detail(tenantId, conversationId));
        return "conversations/conversation :: messages";
    }

    @PostMapping("/app/conversations/{conversationId}/messages")
    String reply(TenantId tenantId, @PathVariable UUID conversationId, @AuthenticationPrincipal ZamuPrincipal user,
            @Validated @ModelAttribute("form") StaffReplyForm form, BindingResult errors, Model model,
            RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                if (form.isTemplate()) {
                    conversations.staffTemplate(tenantId, conversationId, user.userId(), form.templateName(),
                            form.templateLanguage(), form.templateParams());
                } else if (form.text() == null || form.text().isBlank()) {
                    errors.rejectValue("text", "blank", "Type a reply first");
                } else {
                    conversations.staffReply(tenantId, conversationId, user.userId(), form.text());
                }
                if (!errors.hasErrors()) {
                    return "redirect:/app/conversations/" + conversationId;
                }
            } catch (WindowClosedException | TemplateNotAvailableException e) {
                errors.reject("send", e.getMessage());
            }
        }
        addConversation(tenantId, conversationId, model);
        return "conversations/conversation";
    }

    @GetMapping(path = "/app/stream", produces = "text/event-stream")
    @ResponseBody
    SseEmitter stream(TenantId tenantId) {
        return live.subscribe(tenantId);
    }

    @PostMapping("/app/customers/{customerId}/block")
    String block(TenantId tenantId, @PathVariable UUID customerId, @RequestParam boolean blocked,
            @RequestParam UUID conversationId, RedirectAttributes redirect) {
        conversations.setCustomerBlocked(tenantId, customerId, blocked);
        redirect.addFlashAttribute("success", blocked
                ? "Customer blocked. Zamu won't reply to them; their messages still appear here."
                : "Customer unblocked.");
        return "redirect:/app/conversations/" + conversationId;
    }

    private void addList(TenantId tenantId, String status, String q, Model model) {
        InboxFilter filter = InboxFilter.fromParam(status);
        model.addAttribute("rows", conversations.inbox(tenantId, filter, q));
        model.addAttribute("filter", filter);
        model.addAttribute("q", q == null ? "" : q);
    }

    private void addConversation(TenantId tenantId, UUID conversationId, Model model) {
        model.addAttribute("conversation", conversations.detail(tenantId, conversationId));
        model.addAttribute("handover", handovers.current(tenantId, conversationId).orElse(null));
        model.addAttribute("templates", conversations.approvedTemplates(tenantId));
    }
}
