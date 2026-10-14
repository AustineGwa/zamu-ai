package ai.zamu.whatsapp;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
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
import ai.zamu.tenants.TenantService;

/** Setup wizard, WhatsApp step: GET/POST /admin/tenants/{id}/setup/whatsapp (spec: Tenant setup wizard). */
@Controller
@RequestMapping("/admin/tenants/{tenantId}/setup/whatsapp")
class WhatsAppSetupController {

    private final WhatsAppAccountService accounts;
    private final TenantService tenants;
    private final AdminTenantScope scope;
    private final String baseUrl;

    WhatsAppSetupController(WhatsAppAccountService accounts, TenantService tenants, AdminTenantScope scope,
            @Value("${zamu.base-url}") String baseUrl) {
        this.accounts = accounts;
        this.tenants = tenants;
        this.scope = scope;
        this.baseUrl = baseUrl;
    }

    @GetMapping
    String page(@PathVariable UUID tenantId, Model model) {
        TenantId id = scope.forPath(tenantId);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", accounts.view(id)
                    .map(a -> new WhatsAppConnectForm(a.wabaId(), a.phoneNumberId(), a.displayPhone(), ""))
                    .orElseGet(WhatsAppConnectForm::empty));
        }
        addModel(id, model);
        return "whatsapp/setup-whatsapp";
    }

    @PostMapping
    String save(@PathVariable UUID tenantId, @Validated @ModelAttribute("form") WhatsAppConnectForm form,
            BindingResult errors, Model model, RedirectAttributes redirect) {
        TenantId id = scope.forPath(tenantId);
        if (!errors.hasErrors()) {
            try {
                accounts.connect(id, form);
                redirect.addFlashAttribute("success", "WhatsApp connection saved.");
                return "redirect:/admin/tenants/" + tenantId + "/setup/whatsapp";
            } catch (WhatsAppSetupException e) {
                errors.reject("whatsapp", e.getMessage());
            }
        }
        addModel(id, model);
        return "whatsapp/setup-whatsapp";
    }

    private void addModel(TenantId id, Model model) {
        model.addAttribute("tenant", tenants.get(id));
        model.addAttribute("step", "whatsapp");
        model.addAttribute("account", accounts.view(id).orElse(null));
        model.addAttribute("webhookUrl", baseUrl + "/webhooks/whatsapp");
    }
}
