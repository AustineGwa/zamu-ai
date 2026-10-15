package ai.zamu.ai;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.security.AdminTenantScope;
import ai.zamu.shared.security.ZamuPrincipal;
import ai.zamu.tenants.TenantService;

/**
 * Admin console, evaluation (spec: Evaluation screen; routes POST /admin/tenants/{id}/eval/run and GET
 * .../eval/{runId}). Also a "try a question" box that runs one question through the live pipeline.
 */
@Controller
@RequestMapping("/admin/tenants/{tenantId}/eval")
class EvaluationController {

    private final EvaluationService evaluation;
    private final AnswerService answers;
    private final TenantService tenants;
    private final AdminTenantScope scope;

    EvaluationController(EvaluationService evaluation, AnswerService answers, TenantService tenants,
            AdminTenantScope scope) {
        this.evaluation = evaluation;
        this.answers = answers;
        this.tenants = tenants;
        this.scope = scope;
    }

    @GetMapping
    String page(@PathVariable UUID tenantId, Model model) {
        TenantId id = scope.forPath(tenantId);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", EvalCaseForm.empty());
        }
        addModel(id, model);
        return "ai/eval";
    }

    @PostMapping("/cases")
    String addCase(@PathVariable UUID tenantId, @Validated @ModelAttribute("form") EvalCaseForm form,
            BindingResult errors, Model model, RedirectAttributes redirect) {
        TenantId id = scope.forPath(tenantId);
        if (errors.hasErrors()) {
            addModel(id, model);
            return "ai/eval";
        }
        evaluation.addCase(id, form);
        redirect.addFlashAttribute("success", "Question added.");
        return redirectTo(tenantId);
    }

    @PostMapping("/cases/{caseId}/remove")
    String removeCase(@PathVariable UUID tenantId, @PathVariable UUID caseId, RedirectAttributes redirect) {
        evaluation.removeCase(scope.forPath(tenantId), caseId);
        redirect.addFlashAttribute("success", "Question removed from future runs.");
        return redirectTo(tenantId);
    }

    @PostMapping("/traps")
    String addTraps(@PathVariable UUID tenantId, RedirectAttributes redirect) {
        int n = evaluation.addStarterTraps(scope.forPath(tenantId));
        redirect.addFlashAttribute("success", n + " trap questions added.");
        return redirectTo(tenantId);
    }

    @PostMapping("/run")
    String run(@PathVariable UUID tenantId, @AuthenticationPrincipal ZamuPrincipal user, RedirectAttributes redirect) {
        try {
            UUID runId = evaluation.startRun(scope.forPath(tenantId), user.userId());
            return "redirect:/admin/tenants/" + tenantId + "/eval/" + runId;
        } catch (IllegalStateException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return redirectTo(tenantId);
        }
    }

    @PostMapping("/try")
    String tryQuestion(@PathVariable UUID tenantId, @RequestParam String question,
            @RequestParam(defaultValue = "en") String language, Model model) {
        TenantId id = scope.forPath(tenantId);
        if (!question.isBlank()) {
            model.addAttribute("tried", question.strip());
            model.addAttribute("triedLanguage", "sw".equals(language) ? "sw" : "en");
            model.addAttribute("preview", answers.preview(id, question.strip(), "sw".equals(language) ? "sw" : "en"));
        }
        model.addAttribute("form", EvalCaseForm.empty());
        addModel(id, model);
        return "ai/eval";
    }

    @GetMapping("/{runId}")
    String runPage(@PathVariable UUID tenantId, @PathVariable UUID runId, Model model) {
        TenantId id = scope.forPath(tenantId);
        model.addAttribute("tenant", tenants.get(id));
        model.addAttribute("step", "eval");
        model.addAttribute("run", evaluation.run(id, runId));
        model.addAttribute("results", evaluation.results(id, runId));
        return "ai/eval-run";
    }

    /** The progress panel alone, polled by htmx while the run is going. */
    @GetMapping("/{runId}/progress")
    String progress(@PathVariable UUID tenantId, @PathVariable UUID runId, Model model) {
        TenantId id = scope.forPath(tenantId);
        model.addAttribute("tenant", tenants.get(id));
        model.addAttribute("run", evaluation.run(id, runId));
        model.addAttribute("results", evaluation.results(id, runId));
        return "ai/eval-run :: progress";
    }

    private void addModel(TenantId id, Model model) {
        model.addAttribute("tenant", tenants.get(id));
        model.addAttribute("step", "eval");
        model.addAttribute("cases", evaluation.cases(id));
        model.addAttribute("runs", evaluation.recentRuns(id));
        model.addAttribute("kinds", EvalCaseKind.values());
        model.addAttribute("minimum", EvaluationService.MINIMUM_CASES);
        model.addAttribute("liveModel", answers.liveModel());
    }

    private static String redirectTo(UUID tenantId) {
        return "redirect:/admin/tenants/" + tenantId + "/eval";
    }
}
