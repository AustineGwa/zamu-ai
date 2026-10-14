package ai.zamu.leads;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpServletRequest;

import ai.zamu.pricing.PricingService;

/**
 * Public sign-up (lead generation): GET/POST /signup, GET /signup/thanks. Zamu is set up for each business by the
 * team (spec: only admins create tenants), so signing up is a request to be contacted, not a self-serve account.
 */
@Controller
class SignUpController {

    private final LeadService leads;
    private final SignUpRateLimiter rateLimiter;
    private final PricingService pricing;

    SignUpController(LeadService leads, SignUpRateLimiter rateLimiter, PricingService pricing) {
        this.leads = leads;
        this.rateLimiter = rateLimiter;
        this.pricing = pricing;
    }

    /** {@code plan} comes from the pricing page's buttons, e.g. /signup?plan=growth; unknown codes are ignored. */
    @GetMapping("/signup")
    String form(@RequestParam(required = false) String plan, Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", SignUpForm.withPlan(pricing.findActive(plan).map(p -> p.code()).orElse(null)));
        }
        addChoices(model);
        return "leads/signup";
    }

    @PostMapping("/signup")
    String submit(@Validated @ModelAttribute("form") SignUpForm form, BindingResult errors, Model model,
            HttpServletRequest request) {
        if (form.website() != null && !form.website().isBlank()) {
            return "redirect:/signup/thanks"; // honeypot filled: a bot, so pretend it worked and store nothing
        }
        if (!errors.hasErrors()) {
            if (!rateLimiter.tryAcquire(request.getRemoteAddr())) {
                errors.reject("rate", new TooManySignUpsException().getMessage());
            } else {
                try {
                    leads.signUp(form);
                    return "redirect:/signup/thanks";
                } catch (InvalidPhoneException e) {
                    errors.rejectValue("phone", "invalid", e.getMessage());
                }
            }
        }
        addChoices(model);
        return "leads/signup";
    }

    private void addChoices(Model model) {
        model.addAttribute("businessTypes", BusinessType.values());
        model.addAttribute("plans", pricing.catalog().plans());
    }

    @GetMapping("/signup/thanks")
    String thanks() {
        return "leads/thanks";
    }
}
