package ai.zamu.leads;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import ai.zamu.shared.security.ZamuPrincipal;
import ai.zamu.pricing.PricingService;

/**
 * The public home page: what Zamu does, plans, and the way in (sign up or sign in). Signed-in users go straight
 * to their own home instead.
 */
@Controller
class LandingController {

    private final PricingService pricing;

    LandingController(PricingService pricing) {
        this.pricing = pricing;
    }

    @GetMapping("/")
    String landing(@AuthenticationPrincipal ZamuPrincipal user, Model model) {
        if (user != null) {
            return user.isPlatformStaff() ? "redirect:/admin/tenants" : "redirect:/app";
        }
        model.addAttribute("plans", pricing.catalog().plans());
        return "leads/landing";
    }
}
