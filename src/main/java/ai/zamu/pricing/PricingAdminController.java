package ai.zamu.pricing;

import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import ai.zamu.shared.web.NotFoundException;

/**
 * Admin console, price list: GET /admin/pricing, GET/POST /admin/pricing/{id}. Zamu team only (SecurityConfig,
 * /admin/**). ASSUMPTION: the comparison table and the questions are edited in the database for now.
 */
@Controller
@RequestMapping("/admin/pricing")
class PricingAdminController {

    private final PricingService pricing;

    PricingAdminController(PricingService pricing) {
        this.pricing = pricing;
    }

    @GetMapping
    String list(Model model) {
        model.addAttribute("plans", pricing.allPlans());
        return "pricing/admin-list";
    }

    @GetMapping("/{planId}")
    String edit(@PathVariable UUID planId, Model model) {
        PricingPlan plan = find(planId);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", PricingPlanForm.from(plan));
        }
        model.addAttribute("plan", plan);
        return "pricing/admin-edit";
    }

    @PostMapping("/{planId}")
    String save(@PathVariable UUID planId, @Validated @ModelAttribute("form") PricingPlanForm form, BindingResult errors,
            Model model, RedirectAttributes redirect) {
        PricingPlan plan = find(planId);
        if (errors.hasErrors()) {
            model.addAttribute("plan", plan);
            return "pricing/admin-edit";
        }
        pricing.updatePlan(planId, form);
        redirect.addFlashAttribute("success", form.name() + " saved. The website shows the change now.");
        return "redirect:/admin/pricing";
    }

    private PricingPlan find(UUID planId) {
        return pricing.allPlans().stream().filter(p -> p.id().equals(planId)).findFirst()
                .orElseThrow(() -> new NotFoundException("Plan not found"));
    }
}
