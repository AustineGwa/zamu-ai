package ai.zamu.knowledge;

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
 * Admin console, knowledge base editor (spec routes: GET, POST /admin/tenants/{id}/kb and POST
 * .../kb/{entryId}/approve, /archive). Zamu team only; owners suggest changes (spec: version 1 keeps editing with
 * the Zamu team).
 */
@Controller
@RequestMapping("/admin/tenants/{tenantId}/kb")
class KnowledgeAdminController {

    private final KnowledgeService knowledge;
    private final TenantService tenants;
    private final AdminTenantScope scope;

    KnowledgeAdminController(KnowledgeService knowledge, TenantService tenants, AdminTenantScope scope) {
        this.knowledge = knowledge;
        this.tenants = tenants;
        this.scope = scope;
    }

    @GetMapping
    String list(@PathVariable UUID tenantId, Model model) {
        TenantId id = scope.forPath(tenantId);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", KbEntryForm.empty());
        }
        addList(id, model);
        return "knowledge/admin-kb";
    }

    @PostMapping
    String create(@PathVariable UUID tenantId, @AuthenticationPrincipal ZamuPrincipal user,
            @Validated @ModelAttribute("form") KbEntryForm form, BindingResult errors, Model model,
            RedirectAttributes redirect) {
        TenantId id = scope.forPath(tenantId);
        if (errors.hasErrors()) {
            addList(id, model);
            return "knowledge/admin-kb";
        }
        knowledge.create(id, form, user.userId());
        redirect.addFlashAttribute("success", "Saved as a draft. Approve it when the client has confirmed it.");
        return "redirect:/admin/tenants/" + tenantId + "/kb";
    }

    @GetMapping("/{entryId}")
    String edit(@PathVariable UUID tenantId, @PathVariable UUID entryId, Model model) {
        TenantId id = scope.forPath(tenantId);
        KbEntryView entry = knowledge.get(id, entryId);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", KbEntryForm.from(entry));
        }
        model.addAttribute("entry", entry);
        model.addAttribute("tenant", tenants.get(id));
        model.addAttribute("step", "kb");
        return "knowledge/admin-kb-edit";
    }

    @PostMapping("/{entryId}")
    String update(@PathVariable UUID tenantId, @PathVariable UUID entryId, @AuthenticationPrincipal ZamuPrincipal user,
            @Validated @ModelAttribute("form") KbEntryForm form, BindingResult errors, Model model,
            RedirectAttributes redirect) {
        TenantId id = scope.forPath(tenantId);
        if (errors.hasErrors()) {
            model.addAttribute("entry", knowledge.get(id, entryId));
            model.addAttribute("tenant", tenants.get(id));
            model.addAttribute("step", "kb");
            return "knowledge/admin-kb-edit";
        }
        knowledge.update(id, entryId, form, user.userId());
        redirect.addFlashAttribute("success", "Entry saved.");
        return "redirect:/admin/tenants/" + tenantId + "/kb";
    }

    @PostMapping("/{entryId}/approve")
    String approve(@PathVariable UUID tenantId, @PathVariable UUID entryId, @AuthenticationPrincipal ZamuPrincipal user,
            RedirectAttributes redirect) {
        knowledge.approve(scope.forPath(tenantId), entryId, user.userId());
        redirect.addFlashAttribute("success", "Approved. Zamu can use it within a minute.");
        return "redirect:/admin/tenants/" + tenantId + "/kb";
    }

    @PostMapping("/{entryId}/unapprove")
    String unapprove(@PathVariable UUID tenantId, @PathVariable UUID entryId,
            @AuthenticationPrincipal ZamuPrincipal user, RedirectAttributes redirect) {
        knowledge.unapprove(scope.forPath(tenantId), entryId, user.userId());
        redirect.addFlashAttribute("success", "Back to draft. Zamu no longer uses it.");
        return "redirect:/admin/tenants/" + tenantId + "/kb";
    }

    @PostMapping("/{entryId}/archive")
    String archive(@PathVariable UUID tenantId, @PathVariable UUID entryId, @AuthenticationPrincipal ZamuPrincipal user,
            RedirectAttributes redirect) {
        knowledge.archive(scope.forPath(tenantId), entryId, user.userId());
        redirect.addFlashAttribute("success", "Archived. Zamu no longer uses it.");
        return "redirect:/admin/tenants/" + tenantId + "/kb";
    }

    @PostMapping("/reembed")
    String reembedAll(@PathVariable UUID tenantId, RedirectAttributes redirect) {
        int n = knowledge.reembedAll(scope.forPath(tenantId));
        redirect.addFlashAttribute("success", n + " approved entries queued for re-indexing.");
        return "redirect:/admin/tenants/" + tenantId + "/kb";
    }

    private void addList(TenantId id, Model model) {
        model.addAttribute("tenant", tenants.get(id));
        model.addAttribute("step", "kb");
        model.addAttribute("entries", knowledge.list(id));
        model.addAttribute("approvedCount", knowledge.approvedCount(id));
    }
}
