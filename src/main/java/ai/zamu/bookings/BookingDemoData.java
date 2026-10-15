package ai.zamu.bookings;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import ai.zamu.shared.ids.TenantId;
import ai.zamu.tenants.TenantService;
import ai.zamu.tenants.TenantView;

/**
 * Local "h2" profile only: opening hours, two dentists and two services for the demo clinic, so booking can be
 * tried in the WhatsApp simulator ("I want to book a check-up"). Invented example content.
 */
@Component
@Profile("h2")
@ConditionalOnProperty(name = "zamu.local-demo.enabled", havingValue = "true")
@Order(310)
class BookingDemoData implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BookingDemoData.class);

    private final TenantService tenants;
    private final BusinessHoursService hours;
    private final BookingSetupService setup;

    BookingDemoData(TenantService tenants, BusinessHoursService hours, BookingSetupService setup) {
        this.tenants = tenants;
        this.hours = hours;
        this.setup = setup;
    }

    @Override
    public void run(ApplicationArguments args) {
        TenantView demo = tenants.listAll().stream().filter(t -> "Demo Clinic".equals(t.displayName())).findFirst()
                .orElse(null);
        if (demo == null) {
            return;
        }
        TenantId id = TenantId.of(demo.id());
        if (!setup.allServices(id).isEmpty()) {
            return;
        }
        if (!hours.configured(id)) {
            List<DayHours> week = new ArrayList<>();
            for (DayOfWeek day : DayOfWeek.values()) {
                week.add(switch (day) {
                    case SATURDAY -> new DayHours(day, LocalTime.of(9, 0), LocalTime.of(13, 0), LocalTime.of(9, 0), LocalTime.of(13, 0));
                    case SUNDAY -> DayHours.closed(day);
                    default -> new DayHours(day, LocalTime.of(8, 0), LocalTime.of(18, 0), LocalTime.of(8, 0), LocalTime.of(18, 0));
                });
            }
            hours.saveWeek(id, week);
        }
        UUID otieno = setup.createResource(id, new ResourceForm("Dr Otieno", "Dentist", null, true));
        UUID wanjiru = setup.createResource(id, new ResourceForm("Dr Wanjiru", "Dentist", null, true));
        setup.createService(id, new ServiceForm("Consultation", "Kumwona daktari", "General consultation", 30,
                new BigDecimal("2000"), null, BigDecimal.ZERO, 0, true, List.of()));
        setup.createService(id, new ServiceForm("Dental check-up", "Uchunguzi wa meno", null, 45,
                new BigDecimal("2500"), null, new BigDecimal("500"), 15, true, List.of(otieno, wanjiru)));
        log.info("Local demo data: hours, 2 dentists and 2 bookable services for Demo Clinic");
    }
}
