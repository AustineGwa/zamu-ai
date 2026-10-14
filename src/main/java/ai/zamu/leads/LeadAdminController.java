package ai.zamu.leads;

import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Admin console: website sign-ups. GET /admin/leads?status=, POST /admin/leads/{id}/status (operator, admin). */
@Controller
@RequestMapping("/admin/leads")
class LeadAdminController {

    private final LeadService leads;

    LeadAdminController(LeadService leads) {
        this.leads = leads;
    }

    @GetMapping
    String list(@RequestParam(required = false) LeadStatus status, Model model) {
        model.addAttribute("leads", leads.recent(status));
        model.addAttribute("statuses", LeadStatus.values());
        model.addAttribute("selectedStatus", status);
        model.addAttribute("planNames", leads.planNames());
        return "leads/admin-list";
    }

    @PostMapping("/{leadId}/status")
    String changeStatus(@PathVariable UUID leadId, @RequestParam LeadStatus status, RedirectAttributes redirect) {
        leads.changeStatus(leadId, status);
        redirect.addFlashAttribute("success", "Status updated.");
        return "redirect:/admin/leads";
    }
}
