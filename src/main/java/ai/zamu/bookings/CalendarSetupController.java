package ai.zamu.bookings;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.security.AdminTenantScope;
import ai.zamu.tenants.TenantService;

/**
 * Setup wizard, calendar step (BK-01): connect the tenant's Google Calendar by OAuth.
 *
 * <p>ASSUMPTION: one fixed redirect URI, {base-url}/admin/calendar/callback, for every tenant (Google requires each
 * redirect URI to be registered on the OAuth client). The tenant travels in a random {@code state} value kept in
 * the operator's session, never in the URL, and is checked against the operator's access again on return.
 */
@Controller
class CalendarSetupController {

    private static final Logger log = LoggerFactory.getLogger(CalendarSetupController.class);
    private static final String STATE_KEY = "zamu.calendar.oauthState";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final BookingSetupService setup;
    private final TenantService tenants;
    private final AdminTenantScope scope;
    private final String baseUrl;

    CalendarSetupController(BookingSetupService setup, TenantService tenants, AdminTenantScope scope,
            @Value("${zamu.base-url}") String baseUrl) {
        this.setup = setup;
        this.tenants = tenants;
        this.scope = scope;
        this.baseUrl = baseUrl;
    }

    @GetMapping("/admin/tenants/{tenantId}/setup/calendar")
    String page(@PathVariable UUID tenantId, Model model) {
        TenantId id = scope.forPath(tenantId);
        model.addAttribute("tenant", tenants.get(id));
        model.addAttribute("step", "calendar");
        model.addAttribute("calendar", setup.calendarStatus(id));
        model.addAttribute("redirectUri", redirectUri());
        return "bookings/setup-calendar";
    }

    @PostMapping("/admin/tenants/{tenantId}/calendar/connect")
    String connect(@PathVariable UUID tenantId, HttpSession session, RedirectAttributes redirect) {
        TenantId id = scope.forPath(tenantId);
        if (!setup.calendarProviderConfigured()) {
            redirect.addFlashAttribute("error", "Google sign-in isn't set up on this Zamu installation yet.");
            return "redirect:/admin/tenants/" + tenantId + "/setup/calendar";
        }
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        String state = HexFormat.of().formatHex(bytes);
        @SuppressWarnings("unchecked")
        Map<String, UUID> pending = (Map<String, UUID>) session.getAttribute(STATE_KEY);
        Map<String, UUID> states = pending == null ? new HashMap<>() : new HashMap<>(pending);
        states.put(state, id.value());
        session.setAttribute(STATE_KEY, states);
        return "redirect:" + setup.calendarAuthorizationUrl(redirectUri(), state);
    }

    @GetMapping("/admin/calendar/callback")
    String callback(@RequestParam(required = false) String code, @RequestParam(required = false) String state,
            @RequestParam(required = false) String error, HttpSession session, RedirectAttributes redirect) {
        @SuppressWarnings("unchecked")
        Map<String, UUID> states = (Map<String, UUID>) session.getAttribute(STATE_KEY);
        UUID tenantId = states == null || state == null ? null : states.get(state);
        if (tenantId == null) {
            redirect.addFlashAttribute("error", "That calendar sign-in has expired. Please connect again.");
            return "redirect:/admin/tenants";
        }
        Map<String, UUID> remaining = new HashMap<>(states);
        remaining.remove(state);
        session.setAttribute(STATE_KEY, remaining);
        TenantId id = scope.forPath(tenantId);
        if (error != null || code == null) {
            redirect.addFlashAttribute("error", "The calendar wasn't connected: Google sign-in was cancelled.");
        } else {
            try {
                setup.completeCalendarConnection(id, code, redirectUri());
                redirect.addFlashAttribute("success", "Google Calendar connected.");
            } catch (BookingRuleException e) {
                log.info("Calendar connection failed for tenant {}", tenantId);
                redirect.addFlashAttribute("error", e.getMessage());
            }
        }
        return "redirect:/admin/tenants/" + tenantId + "/setup/calendar";
    }

    @PostMapping("/admin/tenants/{tenantId}/calendar/settings")
    String settings(@PathVariable UUID tenantId, @RequestParam(required = false) String calendarId,
            RedirectAttributes redirect) {
        setup.setCalendarId(scope.forPath(tenantId), calendarId);
        redirect.addFlashAttribute("success", "Calendar saved.");
        return "redirect:/admin/tenants/" + tenantId + "/setup/calendar";
    }

    @PostMapping("/admin/tenants/{tenantId}/calendar/disconnect")
    String disconnect(@PathVariable UUID tenantId, RedirectAttributes redirect) {
        setup.disconnectCalendar(scope.forPath(tenantId));
        redirect.addFlashAttribute("success", "Calendar disconnected. Slots now use opening hours and Zamu's bookings only.");
        return "redirect:/admin/tenants/" + tenantId + "/setup/calendar";
    }

    private String redirectUri() {
        return baseUrl + "/admin/calendar/callback";
    }
}
