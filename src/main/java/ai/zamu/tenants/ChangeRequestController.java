package ai.zamu.tenants;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.security.ZamuPrincipal;

/**
 * Change requests: owners send and follow them at /app/requests; the Zamu team works the queue at
 * /admin/change-requests (spec: Settings and Change requests screens).
 */
@Controller
class ChangeRequestController {

    private final ChangeRequestService requests;

    ChangeRequestController(ChangeRequestService requests) {
        this.requests = requests;
    }

    @GetMapping("/app/requests")
    String ownerList(TenantId tenantId, Model model) {
        model.addAttribute("requests", requests.forTenant(tenantId));
        model.addAttribute("types", ChangeRequestType.values());
        return "tenants/requests";
    }

    /** Owners only (SecurityConfig). {@code back} returns to the page the request was made from. */
    @PostMapping("/app/requests")
    String submit(TenantId tenantId, @AuthenticationPrincipal ZamuPrincipal user,
            @RequestParam(defaultValue = "OTHER") ChangeRequestType type, @RequestParam(required = false) String details,
            @RequestParam(required = false) String entry, @RequestParam(required = false) String back,
            RedirectAttributes redirect) {
        try {
            String text = entry == null || entry.isBlank() || details == null || details.isBlank() ? details
                    : "About \"" + entry.strip() + "\": " + details.strip();
            requests.submit(tenantId, user.userId(), type, text);
            redirect.addFlashAttribute("success", "Request sent. The Zamu team will make the change and let you know.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", "Describe the change you'd like.");
        }
        return "redirect:" + (back != null && back.matches("/app/(knowledge|reports|requests)(\\?[\\w=&-]*)?") ? back : "/app/requests");
    }

    @GetMapping("/admin/change-requests")
    String queue(@RequestParam(defaultValue = "true") boolean open, Model model) {
        model.addAttribute("requests", requests.queue(open));
        model.addAttribute("openOnly", open);
        return "tenants/change-requests";
    }

    @PostMapping("/admin/change-requests/{requestId}/close")
    String close(@PathVariable UUID requestId, @RequestParam(defaultValue = "true") boolean done,
            @RequestParam(required = false) String note, @AuthenticationPrincipal ZamuPrincipal user,
            RedirectAttributes redirect) {
        requests.close(requestId, done, user.userId(), note);
        redirect.addFlashAttribute("success", done ? "Marked done." : "Declined.");
        return "redirect:/admin/change-requests";
    }
}
