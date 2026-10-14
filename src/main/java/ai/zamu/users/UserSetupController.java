package ai.zamu.users;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.security.AdminTenantScope;
import ai.zamu.shared.security.ZamuPrincipal;
import ai.zamu.tenants.TenantService;

/**
 * Setup wizard, staff step: the Zamu team adds the tenant's owner and staff logins (spec: Tenant setup wizard).
 * It lives in the users package because users owns the users table; the tenant comes from the path, checked by
 * {@link AdminTenantScope}.
 */
@Controller
@RequestMapping("/admin/tenants/{tenantId}/setup/staff")
class UserSetupController {

    private final UserService users;
    private final TenantService tenants;
    private final AdminTenantScope scope;

    UserSetupController(UserService users, TenantService tenants, AdminTenantScope scope) {
        this.users = users;
        this.tenants = tenants;
        this.scope = scope;
    }

    @GetMapping
    String page(@PathVariable UUID tenantId, Model model) {
        TenantId id = scope.forPath(tenantId);
        addPageModel(id, model);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", CreateUserRequest.empty());
        }
        return "users/setup-staff";
    }

    @PostMapping
    String add(@PathVariable UUID tenantId, @Validated @ModelAttribute("form") CreateUserRequest form,
            BindingResult errors, Model model, RedirectAttributes redirect) {
        TenantId id = scope.forPath(tenantId);
        if (!errors.hasErrors()) {
            try {
                users.addTenantUser(id, form, false);
                redirect.addFlashAttribute("success", "Login added for " + form.name().trim() + ".");
                return "redirect:/admin/tenants/" + tenantId + "/setup/staff";
            } catch (DuplicateEmailException e) {
                errors.rejectValue("email", "duplicate", e.getMessage());
            }
        }
        addPageModel(id, model);
        return "users/setup-staff";
    }

    @PostMapping("/{userId}/disable")
    String disable(@PathVariable UUID tenantId, @PathVariable UUID userId, @AuthenticationPrincipal ZamuPrincipal actor,
            RedirectAttributes redirect) {
        users.disable(scope.forPath(tenantId), userId, actor.userId());
        redirect.addFlashAttribute("success", "Login disabled. They have been signed out everywhere.");
        return "redirect:/admin/tenants/" + tenantId + "/setup/staff";
    }

    private void addPageModel(TenantId id, Model model) {
        model.addAttribute("tenant", tenants.get(id));
        model.addAttribute("users", users.listForTenant(id));
        model.addAttribute("roles", UserController.TENANT_ROLES);
        model.addAttribute("loginLimit", tenants.loginLimit(id));
        model.addAttribute("step", "staff");
    }
}
