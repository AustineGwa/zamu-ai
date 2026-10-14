package ai.zamu.users;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import ai.zamu.shared.crypto.Totp;
import ai.zamu.shared.mail.EmailDeliveryException;
import ai.zamu.shared.security.SessionLogin;
import ai.zamu.shared.security.ZamuPrincipal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Login: email, then a 6-digit emailed code; Zamu operators and admins then also enter an authenticator
 * code (NFR-08). Routes: GET /login, POST /login/code, POST /login/verify (spec: Web routes, Auth). POST /logout
 * is handled by Spring Security (SecurityConfig).
 *
 * <p>The email being logged in is kept in the server-side session between steps, not in the URL, so it never
 * appears in access logs (NFR-13).
 */
@Controller
class LoginController {

    static final String EMAIL_KEY = "zamu.login.email";
    private static final Duration SECOND_FACTOR_WINDOW = Duration.ofMinutes(10);
    private static final int MAX_SECOND_FACTOR_FAILURES = 5;

    private final LoginCodeService codes;
    private final LoginService users;
    private final SessionLogin sessionLogin;
    private final Clock clock;

    LoginController(LoginCodeService codes, LoginService users, SessionLogin sessionLogin, Clock clock) {
        this.codes = codes;
        this.users = users;
        this.sessionLogin = sessionLogin;
        this.clock = clock;
    }

    @GetMapping("/login")
    String loginPage(@AuthenticationPrincipal ZamuPrincipal user, Model model) {
        if (user != null) {
            return "redirect:/";
        }
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new LoginEmailForm(""));
        }
        return "users/login";
    }

    @PostMapping("/login/code")
    String requestCode(@Validated @ModelAttribute("form") LoginEmailForm form, BindingResult errors,
            HttpSession session, RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            return "users/login";
        }
        LoginCodeService.RequestOutcome outcome;
        try {
            outcome = codes.requestCode(form.email());
        } catch (EmailDeliveryException e) {
            errors.reject("email.failed", "We couldn't send the email just now. Please try again in a minute.");
            return "users/login";
        }
        if (outcome == LoginCodeService.RequestOutcome.RATE_LIMITED) {
            errors.reject("rate.limited", "Too many codes requested for this email. Please wait an hour and try again.");
            return "users/login";
        }
        session.setAttribute(EMAIL_KEY, LoginCodeService.normalise(form.email()));
        return "redirect:/login/verify";
    }

    @GetMapping("/login/verify")
    String verifyPage(HttpSession session, Model model) {
        String email = (String) session.getAttribute(EMAIL_KEY);
        if (email == null) {
            return "redirect:/login";
        }
        model.addAttribute("email", email);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new LoginCodeForm(""));
        }
        return "users/verify";
    }

    @PostMapping("/login/verify")
    String verify(@Validated @ModelAttribute("form") LoginCodeForm form, BindingResult errors, HttpSession session,
            Model model, HttpServletRequest request, HttpServletResponse response) {
        String email = (String) session.getAttribute(EMAIL_KEY);
        if (email == null) {
            return "redirect:/login";
        }
        model.addAttribute("email", email);
        if (errors.hasErrors()) {
            return "users/verify";
        }
        Optional<User> user = codes.verify(email, form.code());
        if (user.isEmpty()) {
            errors.rejectValue("code", "invalid",
                    "That code is wrong or has expired. Check the latest email, or request a new code.");
            return "users/verify";
        }
        session.removeAttribute(EMAIL_KEY);

        User verified = user.get();
        if (verified.role().isPlatformStaff()) {
            // NFR-08: Zamu team roles need a second factor before the session is authenticated.
            session.setAttribute(PendingSecondFactor.SESSION_KEY, new PendingSecondFactor(
                    verified.id(), clock.instant().plus(SECOND_FACTOR_WINDOW), null, 0));
            return verified.hasTotp() ? "redirect:/login/totp" : "redirect:/login/totp/setup";
        }
        sessionLogin.login(users.completeLogin(verified.id()), request, response);
        return "redirect:/";
    }

    // ---- Second factor (Zamu operators and admins) ------------------------------------------------

    @GetMapping("/login/totp")
    String totpPage(HttpSession session, Model model) {
        PendingSecondFactor pending = pending(session);
        if (pending == null) {
            return "redirect:/login";
        }
        if (!users.hasSecondFactor(pending.userId())) {
            return "redirect:/login/totp/setup";
        }
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new LoginCodeForm(""));
        }
        return "users/totp";
    }

    @PostMapping("/login/totp")
    String verifyTotp(@Validated @ModelAttribute("form") LoginCodeForm form, BindingResult errors, HttpSession session,
            HttpServletRequest request, HttpServletResponse response, RedirectAttributes redirect) {
        PendingSecondFactor pending = pending(session);
        if (pending == null) {
            return "redirect:/login";
        }
        if (errors.hasErrors()) {
            return "users/totp";
        }
        if (!users.verifySecondFactor(pending.userId(), form.code())) {
            return failSecondFactor(session, pending, errors, "users/totp", redirect);
        }
        session.removeAttribute(PendingSecondFactor.SESSION_KEY);
        sessionLogin.login(users.completeLogin(pending.userId()), request, response);
        return "redirect:/";
    }

    @GetMapping("/login/totp/setup")
    String totpSetupPage(HttpSession session, Model model) {
        PendingSecondFactor pending = pending(session);
        if (pending == null) {
            return "redirect:/login";
        }
        if (users.hasSecondFactor(pending.userId())) {
            return "redirect:/login/totp";
        }
        if (pending.pendingSecret() == null) {
            pending = pending.withSecret(Totp.newSecret());
            session.setAttribute(PendingSecondFactor.SESSION_KEY, pending);
        }
        addSetupModel(model, pending);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new LoginCodeForm(""));
        }
        return "users/totp-setup";
    }

    @PostMapping("/login/totp/setup")
    String enrolTotp(@Validated @ModelAttribute("form") LoginCodeForm form, BindingResult errors, HttpSession session,
            Model model, HttpServletRequest request, HttpServletResponse response, RedirectAttributes redirect) {
        PendingSecondFactor pending = pending(session);
        if (pending == null || pending.pendingSecret() == null) {
            return "redirect:/login";
        }
        addSetupModel(model, pending);
        if (errors.hasErrors()) {
            return "users/totp-setup";
        }
        if (!users.enrolSecondFactor(pending.userId(), pending.pendingSecret(), form.code())) {
            return failSecondFactor(session, pending, errors, "users/totp-setup", redirect);
        }
        session.removeAttribute(PendingSecondFactor.SESSION_KEY);
        sessionLogin.login(users.completeLogin(pending.userId()), request, response);
        return "redirect:/";
    }

    private void addSetupModel(Model model, PendingSecondFactor pending) {
        String email = users.emailOf(pending.userId());
        model.addAttribute("secretGrouped", Totp.grouped(pending.pendingSecret()));
        model.addAttribute("otpauthUri", Totp.otpauthUri("Zamu", email, pending.pendingSecret()));
    }

    private String failSecondFactor(HttpSession session, PendingSecondFactor pending, BindingResult errors,
            String view, RedirectAttributes redirect) {
        PendingSecondFactor updated = pending.withFailure();
        if (updated.failures() >= MAX_SECOND_FACTOR_FAILURES) {
            session.removeAttribute(PendingSecondFactor.SESSION_KEY);
            redirect.addFlashAttribute("error", "Too many wrong codes. Start again with your email.");
            return "redirect:/login";
        }
        session.setAttribute(PendingSecondFactor.SESSION_KEY, updated);
        errors.rejectValue("code", "invalid", "That code didn't match. Use the current code from your authenticator app.");
        return view;
    }

    private PendingSecondFactor pending(HttpSession session) {
        PendingSecondFactor pending = (PendingSecondFactor) session.getAttribute(PendingSecondFactor.SESSION_KEY);
        if (pending == null) {
            return null;
        }
        if (pending.expired(clock.instant())) {
            session.removeAttribute(PendingSecondFactor.SESSION_KEY);
            return null;
        }
        return pending;
    }
}
