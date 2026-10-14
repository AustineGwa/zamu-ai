package ai.zamu.users;

import java.util.List;
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
import ai.zamu.shared.security.ZamuPrincipal;
import ai.zamu.shared.security.ZamuRole;

/**
 * Owner manages staff logins (spec: Permissions, "Manage staff users": owner yes, staff no).
 * Owner-only access is enforced by the URL rule in SecurityConfig. Routes: GET /app/users, POST /app/users, POST /app/users/{id}/disable. The tenant always comes from the
 * session (NFR-09).
 */
@Controller
@RequestMapping("/app/users")
class UserController {

    static final List<ZamuRole> TENANT_ROLES = List.of(ZamuRole.STAFF, ZamuRole.OWNER);

    private final UserService users;

    UserController(UserService users) {
        this.users = users;
    }

    @GetMapping
    String list(TenantId tenantId, Model model) {
        model.addAttribute("users", users.listForTenant(tenantId));
        model.addAttribute("roles", TENANT_ROLES);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", CreateUserRequest.empty());
        }
        return "users/list";
    }

    @PostMapping
    String create(TenantId tenantId, @Validated @ModelAttribute("form") CreateUserRequest form, BindingResult errors,
            Model model, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                users.addTenantUser(tenantId, form, true);
                redirect.addFlashAttribute("success", form.name().trim() + " can now log in with " + form.email().trim() + ".");
                return "redirect:/app/users";
            } catch (DuplicateEmailException e) {
                errors.rejectValue("email", "duplicate", e.getMessage());
            } catch (StaffLimitReachedException e) {
                errors.reject("limit", e.getMessage());
            }
        }
        model.addAttribute("users", users.listForTenant(tenantId));
        model.addAttribute("roles", TENANT_ROLES);
        return "users/list";
    }

    @PostMapping("/{userId}/disable")
    String disable(TenantId tenantId, @PathVariable UUID userId, @AuthenticationPrincipal ZamuPrincipal actor,
            RedirectAttributes redirect) {
        try {
            users.disable(tenantId, userId, actor.userId());
            redirect.addFlashAttribute("success", "Login disabled. They have been signed out everywhere.");
        } catch (CannotDisableSelfException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/app/users";
    }
}
