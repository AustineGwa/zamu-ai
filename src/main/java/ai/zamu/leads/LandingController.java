package ai.zamu.leads;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import ai.zamu.shared.security.ZamuPrincipal;
import ai.zamu.tenants.TenantPlan;

/**
 * The public home page: what Zamu does, plans, and the way in (sign up or sign in). Signed-in users go straight
 * to their own home instead.
 */
@Controller
class LandingController {

    @GetMapping("/")
    String landing(@AuthenticationPrincipal ZamuPrincipal user, Model model) {
        if (user != null) {
            return user.isPlatformStaff() ? "redirect:/admin/tenants" : "redirect:/app";
        }
        model.addAttribute("plans", TenantPlan.values());
        return "leads/landing";
    }
}
