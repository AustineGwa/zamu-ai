package ai.zamu.handovers;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.security.ZamuPrincipal;

/** POST /app/conversations/{id}/accept, /release, /close (spec: Web routes, handovers). */
@Controller
class HandoverController {

    private final HandoverService handovers;

    HandoverController(HandoverService handovers) {
        this.handovers = handovers;
    }

    @PostMapping("/app/conversations/{conversationId}/accept")
    String accept(TenantId tenantId, @PathVariable UUID conversationId, @AuthenticationPrincipal ZamuPrincipal user,
            RedirectAttributes redirect) {
        handovers.accept(tenantId, conversationId, user.userId());
        redirect.addFlashAttribute("success", "You have this chat. The customer sees your replies in the same WhatsApp chat.");
        return "redirect:/app/conversations/" + conversationId;
    }

    @PostMapping("/app/conversations/{conversationId}/release")
    String release(TenantId tenantId, @PathVariable UUID conversationId, RedirectAttributes redirect) {
        handovers.release(tenantId, conversationId);
        redirect.addFlashAttribute("success", "Handed back to Zamu.");
        return "redirect:/app/conversations/" + conversationId;
    }

    @PostMapping("/app/conversations/{conversationId}/close")
    String close(TenantId tenantId, @PathVariable UUID conversationId, RedirectAttributes redirect) {
        handovers.close(tenantId, conversationId);
        redirect.addFlashAttribute("success", "Chat closed.");
        return "redirect:/app/inbox";
    }
}
