package ai.zamu.tenants;

import java.util.Map;
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

import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.security.AdminTenantScope;

/**
 * Admin console: tenant list, create, and the setup wizard steps owned by tenants (profile, tone).
 * Routes: GET/POST /admin/tenants, GET/POST /admin/tenants/{id}/setup/{step} (spec: Web routes, Admin console).
 */
@Controller
@RequestMapping("/admin/tenants")
class TenantController {

    /** Wizard steps built in later milestones (spec: Delivery plan). */
    private static final Map<String, String> LATER_STEPS = Map.of(
            "mpesa", "Milestone 6 (M-Pesa payments)");

    private final TenantService tenants;
    private final AdminTenantScope scope;

    TenantController(TenantService tenants, AdminTenantScope scope) {
        this.tenants = tenants;
        this.scope = scope;
    }

    @GetMapping
    String list(Model model) {
        model.addAttribute("tenants", tenants.listAll());
        model.addAttribute("plans", TenantPlan.values());
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", CreateTenantRequest.empty());
        }
        return "tenants/list";
    }

    @PostMapping
    String create(@Validated @ModelAttribute("form") CreateTenantRequest form, BindingResult errors, Model model,
            RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                TenantView created = tenants.create(form);
                redirect.addFlashAttribute("success", "Tenant created. Continue with the business profile.");
                return "redirect:/admin/tenants/" + created.id() + "/setup/profile";
            } catch (DuplicateTenantNameException e) {
                errors.rejectValue("name", "duplicate", e.getMessage());
            }
        }
        model.addAttribute("tenants", tenants.listAll());
        model.addAttribute("plans", TenantPlan.values());
        return "tenants/list";
    }

    @GetMapping("/{tenantId}/setup/profile")
    String profile(@PathVariable UUID tenantId, Model model) {
        TenantView tenant = tenants.get(scope.forPath(tenantId));
        model.addAttribute("tenant", tenant);
        model.addAttribute("plans", TenantPlan.values());
        model.addAttribute("step", "profile");
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", TenantProfileForm.from(tenant));
        }
        return "tenants/setup-profile";
    }

    @PostMapping("/{tenantId}/setup/profile")
    String saveProfile(@PathVariable UUID tenantId, @Validated @ModelAttribute("form") TenantProfileForm form,
            BindingResult errors, Model model, RedirectAttributes redirect) {
        TenantId id = scope.forPath(tenantId);
        if (!errors.hasErrors()) {
            try {
                tenants.updateProfile(id, form);
                redirect.addFlashAttribute("success", "Business profile saved.");
                return "redirect:/admin/tenants/" + tenantId + "/setup/profile";
            } catch (DuplicateTenantNameException e) {
                errors.rejectValue("name", "duplicate", e.getMessage());
            }
        }
        model.addAttribute("tenant", tenants.get(id));
        model.addAttribute("plans", TenantPlan.values());
        model.addAttribute("step", "profile");
        return "tenants/setup-profile";
    }

    @GetMapping("/{tenantId}/setup/tone")
    String tone(@PathVariable UUID tenantId, Model model) {
        TenantView tenant = tenants.get(scope.forPath(tenantId));
        model.addAttribute("tenant", tenant);
        model.addAttribute("tones", TenantTone.values());
        model.addAttribute("languages", TenantLanguage.values());
        model.addAttribute("step", "tone");
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", TenantToneForm.from(tenant));
        }
        if (!model.containsAttribute("safety")) {
            model.addAttribute("safety", TenantSafetyForm.from(tenant));
        }
        return "tenants/setup-tone";
    }

    @PostMapping("/{tenantId}/setup/tone/safety")
    String saveSafety(@PathVariable UUID tenantId, @Validated @ModelAttribute("safety") TenantSafetyForm safety,
            BindingResult errors, Model model, RedirectAttributes redirect) {
        TenantId id = scope.forPath(tenantId);
        if (errors.hasErrors()) {
            TenantView tenant = tenants.get(id);
            model.addAttribute("tenant", tenant);
            model.addAttribute("tones", TenantTone.values());
            model.addAttribute("languages", TenantLanguage.values());
            model.addAttribute("step", "tone");
            model.addAttribute("form", TenantToneForm.from(tenant));
            return "tenants/setup-tone";
        }
        tenants.updateSafety(id, safety);
        redirect.addFlashAttribute("success", "Safety settings saved.");
        return "redirect:/admin/tenants/" + tenantId + "/setup/tone";
    }

    @PostMapping("/{tenantId}/setup/tone")
    String saveTone(@PathVariable UUID tenantId, @Validated @ModelAttribute("form") TenantToneForm form,
            BindingResult errors, Model model, RedirectAttributes redirect) {
        TenantId id = scope.forPath(tenantId);
        if (errors.hasErrors()) {
            model.addAttribute("tenant", tenants.get(id));
            model.addAttribute("tones", TenantTone.values());
            model.addAttribute("languages", TenantLanguage.values());
            model.addAttribute("step", "tone");
            model.addAttribute("safety", TenantSafetyForm.from(tenants.get(id)));
            return "tenants/setup-tone";
        }
        tenants.updateTone(id, form);
        redirect.addFlashAttribute("success", "Tone and language saved.");
        return "redirect:/admin/tenants/" + tenantId + "/setup/tone";
    }

    /** Steps that arrive in later milestones show where they will be built. */
    @GetMapping("/{tenantId}/setup/{step:calendar|services|mpesa}")
    String laterStep(@PathVariable UUID tenantId, @PathVariable String step, Model model) {
        model.addAttribute("tenant", tenants.get(scope.forPath(tenantId)));
        model.addAttribute("step", step);
        model.addAttribute("milestone", LATER_STEPS.get(step));
        return "tenants/setup-later";
    }
}
