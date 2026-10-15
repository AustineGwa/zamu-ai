package ai.zamu.ops;

import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Admin console: Health and Review queue (spec: admin console screens). Operators and admins. */
@Controller
class OpsController {

    private final OpsService ops;

    OpsController(OpsService ops) {
        this.ops = ops;
    }

    @GetMapping("/admin/health")
    String health(Model model) {
        model.addAttribute("health", ops.health());
        return "ops/health";
    }

    @PostMapping("/admin/jobs/{jobId}/retry")
    String retry(@PathVariable UUID jobId, RedirectAttributes redirect) {
        ops.retry(jobId);
        redirect.addFlashAttribute("success", "Job queued again.");
        return "redirect:/admin/health";
    }

    @GetMapping("/admin/review")
    String review(@RequestParam(defaultValue = "2") int days, Model model) {
        int d = Math.max(1, Math.min(days, 30));
        model.addAttribute("days", d);
        model.addAttribute("items", ops.review(d));
        return "ops/review";
    }
}
