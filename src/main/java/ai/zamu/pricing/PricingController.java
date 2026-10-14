package ai.zamu.pricing;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** The public pricing page: GET /pricing. Every plan, comparison row and question comes from the database. */
@Controller
class PricingController {

    private final PricingService pricing;

    PricingController(PricingService pricing) {
        this.pricing = pricing;
    }

    @GetMapping("/pricing")
    String pricing(Model model) {
        model.addAttribute("catalog", pricing.catalog());
        return "pricing/pricing";
    }
}
