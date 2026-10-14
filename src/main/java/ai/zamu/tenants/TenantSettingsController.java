package ai.zamu.tenants;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import ai.zamu.shared.ids.TenantId;

/**
 * Owner settings (spec: Settings screen; routes GET /app/settings, POST /app/settings/staff-only). Owner-only by
 * the URL rule in SecurityConfig. Hours, holidays and handover settings arrive as change requests later.
 */
@Controller
class TenantSettingsController {

    private final TenantService tenants;

    TenantSettingsController(TenantService tenants) {
        this.tenants = tenants;
    }

    @GetMapping("/app/settings")
    String settings(TenantId tenantId, Model model) {
        model.addAttribute("tenant", tenants.get(tenantId));
        return "tenants/settings";
    }

    @PostMapping("/app/settings/staff-only")
    String staffOnly(TenantId tenantId, @RequestParam boolean enabled, RedirectAttributes redirect) {
        try {
            tenants.setStaffOnly(tenantId, enabled);
            redirect.addFlashAttribute("success", enabled
                    ? "Staff-only mode is on. Zamu has stopped replying; every chat goes to the inbox."
                    : "Staff-only mode is off. Zamu is replying to customers again.");
        } catch (IllegalTenantStatusException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/app/settings";
    }

    @PostMapping("/app/settings/holding-reply")
    String holdingReply(TenantId tenantId, @RequestParam(required = false) String holdingReply,
            RedirectAttributes redirect) {
        if (holdingReply != null && holdingReply.length() > 500) {
            redirect.addFlashAttribute("error", "Keep the holding reply under 500 characters.");
        } else {
            tenants.updateHoldingReply(tenantId, holdingReply);
            redirect.addFlashAttribute("success", "Holding reply saved.");
        }
        return "redirect:/app/settings";
    }
}
