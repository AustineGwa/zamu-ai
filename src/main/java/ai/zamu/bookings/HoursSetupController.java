package ai.zamu.bookings;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.UUID;

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
import ai.zamu.tenants.TenantService;

/** Setup wizard, hours step: GET/POST /admin/tenants/{id}/setup/hours (spec: Tenant setup wizard). */
@Controller
@RequestMapping("/admin/tenants/{tenantId}/setup/hours")
class HoursSetupController {

    private final BusinessHoursService hours;
    private final BookingSetupService setup;
    private final TenantService tenants;
    private final AdminTenantScope scope;

    HoursSetupController(BusinessHoursService hours, BookingSetupService setup, TenantService tenants,
            AdminTenantScope scope) {
        this.hours = hours;
        this.setup = setup;
        this.tenants = tenants;
        this.scope = scope;
    }

    /** Holidays: whole days the business is closed (spec: holidays table; BK-02). */
    @PostMapping("/holidays")
    String addHoliday(@PathVariable UUID tenantId, @RequestParam(required = false) String day,
            @RequestParam(required = false) String note, RedirectAttributes redirect) {
        TenantId id = scope.forPath(tenantId);
        try {
            setup.addHoliday(id, java.time.LocalDate.parse(day == null ? "" : day.strip()),
                    note == null ? null : note.strip());
            redirect.addFlashAttribute("success", "Closed day added.");
        } catch (java.time.format.DateTimeParseException e) {
            redirect.addFlashAttribute("error", "Choose a date.");
        } catch (BookingRuleException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/tenants/" + tenantId + "/setup/hours";
    }

    @PostMapping("/holidays/{holidayId}/remove")
    String removeHoliday(@PathVariable UUID tenantId, @PathVariable UUID holidayId, RedirectAttributes redirect) {
        setup.removeHoliday(scope.forPath(tenantId), holidayId);
        redirect.addFlashAttribute("success", "Closed day removed.");
        return "redirect:/admin/tenants/" + tenantId + "/setup/hours";
    }

    @GetMapping
    String page(@PathVariable UUID tenantId, Model model) {
        TenantId id = scope.forPath(tenantId);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", HoursForm.from(hours.week(id)));
        }
        addModel(id, model);
        return "bookings/setup-hours";
    }

    @PostMapping
    String save(@PathVariable UUID tenantId, @Validated @ModelAttribute("form") HoursForm form, BindingResult errors,
            Model model, RedirectAttributes redirect) {
        TenantId id = scope.forPath(tenantId);
        if (!errors.hasErrors()) {
            try {
                hours.saveWeek(id, form.toWeek());
                redirect.addFlashAttribute("success", "Hours saved.");
                return "redirect:/admin/tenants/" + tenantId + "/setup/hours";
            } catch (InvalidHoursException e) {
                errors.reject("hours", e.getMessage());
            }
        }
        addModel(id, model);
        return "bookings/setup-hours";
    }

    private void addModel(TenantId id, Model model) {
        model.addAttribute("tenant", tenants.get(id));
        model.addAttribute("step", "hours");
        model.addAttribute("holidays", setup.upcomingHolidays(id));
        model.addAttribute("dayNames", Arrays.stream(DayOfWeek.values()).map(BusinessHoursService::label).toList());
    }
}
