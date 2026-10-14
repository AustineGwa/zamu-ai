package ai.zamu.whatsapp;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.security.AdminTenantScope;
import ai.zamu.tenants.TenantService;

/**
 * Admin console, WhatsApp templates for a tenant: /admin/tenants/{id}/templates. ASSUMPTION: the spec lists
 * wa_templates but no route; templates are managed by the Zamu team next to the WhatsApp setup step.
 */
@Controller
@RequestMapping("/admin/tenants/{tenantId}/templates")
class TemplateController {

    private final TemplateService templates;
    private final TenantService tenants;
    private final AdminTenantScope scope;

    TemplateController(TemplateService templates, TenantService tenants, AdminTenantScope scope) {
        this.templates = templates;
        this.tenants = tenants;
        this.scope = scope;
    }

    @GetMapping
    String list(@PathVariable UUID tenantId, Model model) {
        TenantId id = scope.forPath(tenantId);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", WaTemplateForm.empty());
        }
        addModel(id, model);
        return "whatsapp/templates";
    }

    @PostMapping
    String create(@PathVariable UUID tenantId, @Validated @ModelAttribute("form") WaTemplateForm form,
            BindingResult errors, Model model, RedirectAttributes redirect) {
        TenantId id = scope.forPath(tenantId);
        if (!errors.hasErrors()) {
            try {
                templates.create(id, form);
                redirect.addFlashAttribute("success", "Template saved as a draft. Submit it to Meta for review.");
                return redirectTo(tenantId);
            } catch (WaTemplateException e) {
                errors.reject("template", e.getMessage());
            }
        }
        addModel(id, model);
        return "whatsapp/templates";
    }

    @PostMapping("/{templateId}/submit")
    String submit(@PathVariable UUID tenantId, @PathVariable UUID templateId, RedirectAttributes redirect) {
        try {
            templates.submit(scope.forPath(tenantId), templateId);
            redirect.addFlashAttribute("success", "Sent to Meta for review. The status updates when Meta decides.");
        } catch (WaTemplateException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return redirectTo(tenantId);
    }

    @PostMapping("/{templateId}/status")
    String recordStatus(@PathVariable UUID tenantId, @PathVariable UUID templateId,
            @RequestParam WaTemplateStatus status, @RequestParam(required = false) String reason,
            RedirectAttributes redirect) {
        templates.recordStatus(scope.forPath(tenantId), templateId, status, reason);
        redirect.addFlashAttribute("success", "Status recorded.");
        return redirectTo(tenantId);
    }

    private void addModel(TenantId id, Model model) {
        model.addAttribute("tenant", tenants.get(id));
        model.addAttribute("step", "whatsapp");
        model.addAttribute("templates", templates.list(id));
        model.addAttribute("statuses", WaTemplateStatus.values());
    }

    private static String redirectTo(UUID tenantId) {
        return "redirect:/admin/tenants/" + tenantId + "/templates";
    }
}
