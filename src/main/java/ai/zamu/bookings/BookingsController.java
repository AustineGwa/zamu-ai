package ai.zamu.bookings;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.phone.KenyanPhone;
import ai.zamu.tenants.TenantService;

/**
 * Client dashboard bookings (spec: Today and Bookings screens; BK-09, BK-13): today's list, a list by date
 * range, staff bookings with a slot picker, and move, cancel, deposit received, completed and no-show.
 * The tenant always comes from the session (NFR-09).
 */
@Controller
class BookingsController {

    private final BookingService bookings;
    private final CustomerDirectory customers;
    private final TenantService tenants;
    private final Clock clock;

    BookingsController(BookingService bookings, CustomerDirectory customers, TenantService tenants, Clock clock) {
        this.bookings = bookings;
        this.customers = customers;
        this.tenants = tenants;
        this.clock = clock;
    }

    @GetMapping("/app/today")
    String today(TenantId tenantId, Model model) {
        ZoneId zone = zone(tenantId);
        LocalDate day = LocalDate.now(clock.withZone(zone));
        model.addAttribute("day", day);
        model.addAttribute("zone", zone);
        model.addAttribute("bookings", bookings.between(tenantId, day.atStartOfDay(zone).toInstant(),
                day.plusDays(1).atStartOfDay(zone).toInstant(), false));
        model.addAttribute("now", clock.instant());
        return "bookings/today";
    }

    @GetMapping("/app/bookings")
    String list(TenantId tenantId, @RequestParam(required = false) String from,
            @RequestParam(required = false) String to, @RequestParam(defaultValue = "false") boolean cancelled,
            Model model) {
        ZoneId zone = zone(tenantId);
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate start = parse(from, today);
        LocalDate end = parse(to, start.plusDays(13));
        if (end.isBefore(start)) {
            end = start;
        }
        if (end.isAfter(start.plusDays(92))) {
            end = start.plusDays(92);
        }
        model.addAttribute("from", start);
        model.addAttribute("to", end);
        model.addAttribute("cancelled", cancelled);
        model.addAttribute("zone", zone);
        model.addAttribute("now", clock.instant());
        model.addAttribute("bookings", bookings.between(tenantId, start.atStartOfDay(zone).toInstant(),
                end.plusDays(1).atStartOfDay(zone).toInstant(), cancelled));
        return "bookings/list";
    }

    @GetMapping("/app/bookings/new")
    String newBooking(TenantId tenantId, Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", StaffBookingForm.empty());
        }
        addFormModel(tenantId, model);
        return "bookings/new";
    }

    /** The slot picker fragment (htmx): free times for a service on a day. */
    @GetMapping("/app/bookings/slots")
    String slots(TenantId tenantId, @RequestParam(required = false) UUID serviceId,
            @RequestParam(required = false) String date, @RequestParam(required = false) UUID moving, Model model) {
        ZoneId zone = zone(tenantId);
        model.addAttribute("zone", zone);
        if (serviceId == null || date == null || date.isBlank()) {
            model.addAttribute("slots", null);
            return "bookings/slots :: slots";
        }
        try {
            model.addAttribute("slots", bookings.slotsOn(tenantId, serviceId, LocalDate.parse(date), moving));
        } catch (DateTimeParseException e) {
            model.addAttribute("slots", null);
        } catch (CalendarUnavailableException e) {
            model.addAttribute("calendarError", true);
            model.addAttribute("slots", java.util.List.of());
        }
        return "bookings/slots :: slots";
    }

    @PostMapping("/app/bookings")
    String create(TenantId tenantId, @Validated @ModelAttribute("form") StaffBookingForm form, BindingResult errors,
            Model model, RedirectAttributes redirect) {
        String phone = KenyanPhone.normalise(form.phone() == null ? "" : form.phone()).orElse(null);
        if (phone == null && !errors.hasFieldErrors("phone")) {
            errors.rejectValue("phone", "phone", "Enter a Kenyan mobile number, like 0712 345 678");
        }
        if (!errors.hasErrors()) {
            try {
                String[] slot = form.slot().split(":");
                UUID customerId = customers.findOrCreate(tenantId, phone, form.name());
                Booking b = bookings.hold(tenantId, new BookingService.HoldRequest(customerId, blankToNull(form.name()),
                        phone, null, form.serviceId(), "0".equals(slot[0]) ? null : UUID.fromString(slot[0]),
                        Instant.ofEpochSecond(Long.parseLong(slot[1])), "staff", blankToNull(form.notes())));
                redirect.addFlashAttribute("success", b.status() == BookingStatus.CONFIRMED ? "Booking confirmed."
                        : "Booking saved, waiting for the deposit.");
                return "redirect:/app/bookings";
            } catch (BookingSlotTakenException e) {
                errors.reject("slot", "That time was just taken. Choose another.");
            } catch (CalendarUnavailableException e) {
                errors.reject("slot", "The calendar can't be checked right now. Try again in a minute.");
            } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
                errors.reject("slot", "Choose a time.");
            }
        }
        addFormModel(tenantId, model);
        return "bookings/new";
    }

    @GetMapping("/app/bookings/{bookingId}/move")
    String movePage(TenantId tenantId, @PathVariable UUID bookingId, Model model) {
        Booking b = bookings.get(tenantId, bookingId);
        model.addAttribute("booking", b);
        model.addAttribute("zone", zone(tenantId));
        model.addAttribute("day", b.startsAt().atZone(zone(tenantId)).toLocalDate());
        return "bookings/move";
    }

    @PostMapping("/app/bookings/{bookingId}/move")
    String move(TenantId tenantId, @PathVariable UUID bookingId, @RequestParam(required = false) String slot,
            RedirectAttributes redirect) {
        try {
            String[] parts = slot.split(":");
            bookings.move(tenantId, bookingId, "0".equals(parts[0]) ? null : UUID.fromString(parts[0]),
                    Instant.ofEpochSecond(Long.parseLong(parts[1])), "staff");
            redirect.addFlashAttribute("success", "Booking moved. The customer has been told.");
            return "redirect:/app/bookings";
        } catch (BookingSlotTakenException e) {
            redirect.addFlashAttribute("error", "That time was just taken. Choose another.");
        } catch (BookingRuleException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (RuntimeException e) {
            redirect.addFlashAttribute("error", "Choose a time.");
        }
        return "redirect:/app/bookings/" + bookingId + "/move";
    }

    @PostMapping("/app/bookings/{bookingId}/cancel")
    String cancel(TenantId tenantId, @PathVariable UUID bookingId, RedirectAttributes redirect) {
        bookings.cancel(tenantId, bookingId, "staff");
        redirect.addFlashAttribute("success", "Booking cancelled. The customer has been told.");
        return "redirect:/app/bookings";
    }

    /** A deposit paid another way (cash, or M-Pesa before requests are switched on): confirm by hand. */
    @PostMapping("/app/bookings/{bookingId}/confirm")
    String confirm(TenantId tenantId, @PathVariable UUID bookingId, RedirectAttributes redirect) {
        try {
            bookings.confirm(tenantId, bookingId, true);
            redirect.addFlashAttribute("success", "Booking confirmed. The customer has been told.");
        } catch (BookingRuleException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/app/bookings";
    }

    @PostMapping("/app/bookings/{bookingId}/status")
    String status(TenantId tenantId, @PathVariable UUID bookingId, @RequestParam String outcome,
            @RequestParam(defaultValue = "/app/today") String back, RedirectAttributes redirect) {
        try {
            bookings.markOutcome(tenantId, bookingId, "no_show".equals(outcome) ? BookingStatus.NO_SHOW
                    : BookingStatus.COMPLETED);
            redirect.addFlashAttribute("success", "Saved.");
        } catch (BookingRuleException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:" + ("/app/bookings".equals(back) ? "/app/bookings" : "/app/today");
    }

    private void addFormModel(TenantId tenantId, Model model) {
        model.addAttribute("services", bookings.services(tenantId));
        model.addAttribute("today", LocalDate.now(clock.withZone(zone(tenantId))));
    }

    private ZoneId zone(TenantId tenantId) {
        return ZoneId.of(tenants.get(tenantId).timezone());
    }

    private static LocalDate parse(String value, LocalDate fallback) {
        try {
            return value == null || value.isBlank() ? fallback : LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return fallback;
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
