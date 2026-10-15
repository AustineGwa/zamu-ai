package ai.zamu.knowledge;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import ai.zamu.shared.ids.TenantId;

/** GET /app/knowledge: the approved knowledge base, read-only for owners and staff (spec: Web routes). */
@Controller
class KnowledgeOwnerController {

    private final KnowledgeService knowledge;

    KnowledgeOwnerController(KnowledgeService knowledge) {
        this.knowledge = knowledge;
    }

    @GetMapping("/app/knowledge")
    String knowledge(TenantId tenantId, Model model) {
        model.addAttribute("entries", knowledge.approved(tenantId));
        return "knowledge/owner-kb";
    }
}
