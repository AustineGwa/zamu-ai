package ai.zamu.golive;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.security.AdminTenantScope;
import ai.zamu.tenants.TenantService;

/** Setup wizard, go-live step: GET /admin/tenants/{id}/setup/go-live, POST .../go-live and .../pause. */
@Controller
@RequestMapping("/admin/tenants/{tenantId}/setup/go-live")
class GoLiveController {

    private final GoLiveService goLive;
    private final TenantService tenants;
    private final AdminTenantScope scope;

    GoLiveController(GoLiveService goLive, TenantService tenants, AdminTenantScope scope) {
        this.goLive = goLive;
        this.tenants = tenants;
        this.scope = scope;
    }

    @GetMapping
    String page(@PathVariable UUID tenantId, Model model) {
        TenantId id = scope.forPath(tenantId);
        List<GoLiveCheck> checks = goLive.checks(id);
        model.addAttribute("tenant", tenants.get(id));
        model.addAttribute("step", "go-live");
        model.addAttribute("checks", checks);
        model.addAttribute("ready", goLive.ready(checks));
        return "golive/go-live";
    }

    @PostMapping
    String goLive(@PathVariable UUID tenantId, RedirectAttributes redirect) {
        try {
            goLive.goLive(scope.forPath(tenantId));
            redirect.addFlashAttribute("success", "Live. Zamu is now answering this business's customers.");
        } catch (IllegalStateException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/tenants/" + tenantId + "/setup/go-live";
    }

    @PostMapping("/pause")
    String pause(@PathVariable UUID tenantId, RedirectAttributes redirect) {
        try {
            goLive.pause(scope.forPath(tenantId));
            redirect.addFlashAttribute("success", "Paused. Zamu has stopped replying; chats go to the inbox.");
        } catch (IllegalStateException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/tenants/" + tenantId + "/setup/go-live";
    }
}
