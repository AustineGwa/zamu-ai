package ai.zamu.bookings;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ai.zamu.audit.AuditEvent;
import ai.zamu.audit.AuditService;
import ai.zamu.shared.crypto.SecretCipher;
import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.ids.UuidV7;
import ai.zamu.shared.web.NotFoundException;
import ai.zamu.tenants.TenantService;

/**
 * Setup wizard data for bookings: services and resources, booking rules, holidays and the calendar connection
 * (spec: Tenant setup wizard; BK-01, BK-02, BK-04).
 */
@Service
public class BookingSetupService {

    /** What the calendar step shows. */
    public record CalendarStatus(boolean providerConfigured, boolean connected, boolean revoked, String calendarId,
            String lastError) {
    }

    private final BookingCatalogRepository catalog;
    private final CalendarConnectionRepository connections;
    private final CalendarApi api;
    private final CalendarGateway gateway;
    private final GoogleProperties google;
    private final SecretCipher cipher;
    private final TenantService tenants;
    private final AuditService audit;
    private final Clock clock;

    BookingSetupService(BookingCatalogRepository catalog, CalendarConnectionRepository connections, CalendarApi api,
            CalendarGateway gateway, GoogleProperties google, SecretCipher cipher, TenantService tenants,
            AuditService audit, Clock clock) {
        this.catalog = catalog;
        this.connections = connections;
        this.api = api;
        this.gateway = gateway;
        this.google = google;
        this.cipher = cipher;
        this.tenants = tenants;
        this.audit = audit;
        this.clock = clock;
    }

    // --- services and resources ---

    @Transactional(readOnly = true)
    public List<BookableService> allServices(TenantId tenantId) {
        return catalog.findServices(tenantId, false);
    }

    @Transactional(readOnly = true)
    public BookableService service(TenantId tenantId, UUID id) {
        return catalog.findService(tenantId, id).orElseThrow(() -> new NotFoundException("Service not found"));
    }

    @Transactional(readOnly = true)
    public List<BookingResource> allResources(TenantId tenantId) {
        return catalog.findResources(tenantId, false);
    }

    @Transactional
    public UUID createService(TenantId tenantId, ServiceForm form) {
        check(form);
        UUID id = UuidV7.next(clock);
        catalog.insertService(tenantId, id, form, clock.instant());
        catalog.replaceServiceResources(tenantId, id, ownResources(tenantId, form.resourceIds()));
        audit.record(AuditEvent.of(tenantId, "service.created", "service", id).withAfter(Map.of("name", form.name())));
        return id;
    }

    @Transactional
    public void updateService(TenantId tenantId, UUID id, ServiceForm form) {
        check(form);
        if (catalog.updateService(tenantId, id, form, clock.instant()) == 0) {
            throw new NotFoundException("Service not found");
        }
        catalog.replaceServiceResources(tenantId, id, ownResources(tenantId, form.resourceIds()));
        audit.record(AuditEvent.of(tenantId, "service.updated", "service", id));
    }

    private static void check(ServiceForm form) {
        if (form.priceMin() != null && form.priceMax() != null && form.priceMax().compareTo(form.priceMin()) < 0) {
            throw new BookingRuleException("The highest price can't be lower than the lowest.");
        }
    }

    /** Only this tenant's resources can be linked (NFR-09): IDs from the form are checked, not trusted. */
    private List<UUID> ownResources(TenantId tenantId, List<UUID> ids) {
        List<UUID> own = catalog.findResources(tenantId, false).stream().map(BookingResource::id).toList();
        return ids.stream().filter(own::contains).distinct().toList();
    }

    @Transactional
    public UUID createResource(TenantId tenantId, ResourceForm form) {
        UUID id = UuidV7.next(clock);
        catalog.insertResource(tenantId, id, form, clock.instant());
        audit.record(AuditEvent.of(tenantId, "resource.created", "resource", id));
        return id;
    }

    @Transactional
    public void updateResource(TenantId tenantId, UUID id, ResourceForm form) {
        if (catalog.updateResource(tenantId, id, form, clock.instant()) == 0) {
            throw new NotFoundException("Resource not found");
        }
        audit.record(AuditEvent.of(tenantId, "resource.updated", "resource", id));
    }

    // --- rules ---

    @Transactional(readOnly = true)
    public BookingSettings settings(TenantId tenantId) {
        return catalog.findSettings(tenantId).orElse(BookingSettings.DEFAULTS);
    }

    @Transactional
    public void saveSettings(TenantId tenantId, BookingSettings settings) {
        catalog.saveSettings(tenantId, settings, clock.instant());
        audit.record(AuditEvent.of(tenantId, "booking_settings.updated", "tenant", tenantId.value()));
    }

    // --- holidays ---

    @Transactional(readOnly = true)
    public List<Holiday> upcomingHolidays(TenantId tenantId) {
        ZoneId zone = ZoneId.of(tenants.get(tenantId).timezone());
        return catalog.findHolidays(tenantId, LocalDate.now(clock.withZone(zone)));
    }

    @Transactional
    public void addHoliday(TenantId tenantId, LocalDate day, String note) {
        if (catalog.holidayExists(tenantId, day)) {
            throw new BookingRuleException("That day is already closed.");
        }
        UUID id = UuidV7.next(clock);
        catalog.insertHoliday(tenantId, id, day, note, clock.instant());
        audit.record(AuditEvent.of(tenantId, "holiday.added", "holiday", id).withAfter(Map.of("day", day.toString())));
    }

    @Transactional
    public void removeHoliday(TenantId tenantId, UUID id) {
        if (catalog.deleteHoliday(tenantId, id) == 0) {
            throw new NotFoundException("Holiday not found");
        }
        audit.record(AuditEvent.of(tenantId, "holiday.removed", "holiday", id));
    }

    // --- calendar (BK-01) ---

    @Transactional(readOnly = true)
    public CalendarStatus calendarStatus(TenantId tenantId) {
        Optional<CalendarConnectionRepository.Row> row = connections.find(tenantId);
        return new CalendarStatus(google.configured(), row.map(CalendarConnectionRepository.Row::connected).orElse(false),
                row.map(r -> "revoked".equals(r.status())).orElse(false),
                row.map(CalendarConnectionRepository.Row::calendarId).orElse(null),
                row.map(CalendarConnectionRepository.Row::lastError).orElse(null));
    }

    public boolean calendarProviderConfigured() {
        return google.configured();
    }

    public String calendarAuthorizationUrl(String redirectUri, String state) {
        if (!google.configured()) {
            throw new BookingRuleException("Google sign-in isn't set up on this Zamu installation yet.");
        }
        return api.authorizationUrl(redirectUri, state);
    }

    /** Completes Google's consent: stores the refresh token encrypted and uses the primary calendar. */
    @Transactional
    public void completeCalendarConnection(TenantId tenantId, String code, String redirectUri) {
        CalendarApi.Tokens tokens;
        try {
            tokens = api.exchangeCode(code, redirectUri);
        } catch (CalendarApiException e) {
            throw new BookingRuleException("Google didn't accept the sign-in. Please try again.");
        }
        if (tokens.refreshToken() == null) {
            throw new BookingRuleException("Google didn't grant offline access. Remove Zamu from the Google account's "
                    + "third-party access and connect again.");
        }
        String calendarId = connections.find(tenantId).map(CalendarConnectionRepository.Row::calendarId).orElse("primary");
        connections.save(tenantId, UuidV7.next(clock), calendarId, cipher.encryptString(tokens.refreshToken()),
                clock.instant());
        gateway.forget(tenantId);
        audit.record(AuditEvent.of(tenantId, "calendar.connected", "tenant", tenantId.value()));
    }

    @Transactional
    public void setCalendarId(TenantId tenantId, String calendarId) {
        String id = calendarId == null || calendarId.isBlank() ? "primary" : calendarId.strip();
        connections.setCalendarId(tenantId, id, clock.instant());
        audit.record(AuditEvent.of(tenantId, "calendar.updated", "tenant", tenantId.value()));
    }

    @Transactional
    public void disconnectCalendar(TenantId tenantId) {
        connections.delete(tenantId);
        gateway.forget(tenantId);
        audit.record(AuditEvent.of(tenantId, "calendar.disconnected", "tenant", tenantId.value()));
    }
}
