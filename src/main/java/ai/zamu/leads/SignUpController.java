package ai.zamu.leads;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Public sign-up (lead generation): GET/POST /signup, GET /signup/thanks. Zamu is set up for each business by the
 * team (spec: only admins create tenants), so signing up is a request to be contacted, not a self-serve account.
 */
@Controller
class SignUpController {

    private final LeadService leads;
    private final SignUpRateLimiter rateLimiter;

    SignUpController(LeadService leads, SignUpRateLimiter rateLimiter) {
        this.leads = leads;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/signup")
    String form(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", SignUpForm.empty());
        }
        model.addAttribute("businessTypes", BusinessType.values());
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
        model.addAttribute("businessTypes", BusinessType.values());
        return "leads/signup";
    }

    @GetMapping("/signup/thanks")
    String thanks() {
        return "leads/thanks";
    }
}
