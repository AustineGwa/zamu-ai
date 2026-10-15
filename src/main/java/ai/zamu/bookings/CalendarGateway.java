package ai.zamu.bookings;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import ai.zamu.shared.crypto.SecretCipher;
import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.mail.EmailMessage;
import ai.zamu.shared.mail.EmailSender;
import ai.zamu.users.UserService;

/**
 * A tenant's calendar, if one is connected (BK-01, BK-02, BK-05, BK-07, BK-10). Without a connection every call
 * is a no-op and availability comes from business hours and Zamu's own bookings.
 *
 * <p>Access tokens are cached per tenant until shortly before expiry; free/busy results for at most 60 seconds
 * (spec: INT Google Calendar), and never when re-checking just before a booking is held.
 */
@Component
class CalendarGateway {

    private static final Logger log = LoggerFactory.getLogger(CalendarGateway.class);
    static final String MAIN = "main";

    private final CalendarConnectionRepository connections;
    private final CalendarApi api;
    private final SecretCipher cipher;
    private final UserService users;
    private final EmailSender email;
    private final Clock clock;
    private final String baseUrl;
    private final Cache<UUID, String> accessTokens = Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(45))
            .maximumSize(10_000).build();
    private final Cache<String, Map<String, List<CalendarApi.Busy>>> freeBusy = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60)).maximumSize(10_000).build();

    CalendarGateway(CalendarConnectionRepository connections, CalendarApi api, SecretCipher cipher, UserService users,
            EmailSender email, Clock clock, @Value("${zamu.base-url}") String baseUrl) {
        this.connections = connections;
        this.api = api;
        this.cipher = cipher;
        this.users = users;
        this.email = email;
        this.clock = clock;
        this.baseUrl = baseUrl;
    }

    boolean connected(TenantId tenantId) {
        return connections.find(tenantId).map(CalendarConnectionRepository.Row::connected).orElse(false);
    }

    /**
     * Busy periods keyed by calendar: {@link #MAIN} for the tenant's calendar, otherwise a resource's own calendar
     * ID. Empty when no calendar is connected.
     *
     * @throws CalendarUnavailableException when the calendar can't be read; callers must not book blind
     */
    Map<String, List<CalendarApi.Busy>> busy(TenantId tenantId, Set<String> resourceCalendars, Instant from,
            Instant to, boolean fresh) {
        Optional<CalendarConnectionRepository.Row> connection = connections.find(tenantId);
        if (connection.isEmpty()) {
            return Map.of();
        }
        if (!connection.get().connected()) {
            throw new CalendarUnavailableException("The calendar connection needs to be renewed");
        }
        String mainId = connection.get().calendarId();
        Set<String> ids = new LinkedHashSet<>();
        ids.add(mainId);
        ids.addAll(resourceCalendars);
        String key = tenantId.value() + "|" + from + "|" + to + "|" + ids;
        if (!fresh) {
            Map<String, List<CalendarApi.Busy>> cached = freeBusy.getIfPresent(key);
            if (cached != null) {
                return cached;
            }
        }
        try {
            Map<String, List<CalendarApi.Busy>> raw = withToken(tenantId, connection.get(),
                    token -> api.freeBusy(token, new ArrayList<>(ids), from, to));
            Map<String, List<CalendarApi.Busy>> result = new java.util.HashMap<>(raw);
            result.put(MAIN, raw.getOrDefault(mainId, List.of()));
            freeBusy.put(key, result);
            return result;
        } catch (CalendarApiException e) {
            throw new CalendarUnavailableException(e.getMessage());
        }
    }

    /** Creates the event; returns its ID, or null when there's no calendar or the call failed (logged). */
    String createEvent(TenantId tenantId, Booking booking, boolean tentative) {
        return connections.find(tenantId).filter(CalendarConnectionRepository.Row::connected).map(c -> {
            try {
                return withToken(tenantId, c, token -> api.insertEvent(token, c.calendarId(), details(booking, tentative)));
            } catch (CalendarApiException e) {
                log.warn("Calendar event for booking {} not created: {}", booking.id(), e.getMessage());
                return null;
            }
        }).orElse(null);
    }

    void updateEvent(TenantId tenantId, Booking booking, boolean tentative) {
        if (booking.calendarEventId() == null) {
            return;
        }
        connections.find(tenantId).filter(CalendarConnectionRepository.Row::connected).ifPresent(c -> {
            try {
                withToken(tenantId, c, token -> {
                    api.patchEvent(token, c.calendarId(), booking.calendarEventId(), details(booking, tentative));
                    return null;
                });
            } catch (CalendarApiException e) {
                log.warn("Calendar event for booking {} not updated: {}", booking.id(), e.getMessage());
            }
        });
    }

    void deleteEvent(TenantId tenantId, String eventId) {
        if (eventId == null) {
            return;
        }
        connections.find(tenantId).filter(CalendarConnectionRepository.Row::connected).ifPresent(c -> {
            try {
                withToken(tenantId, c, token -> {
                    api.deleteEvent(token, c.calendarId(), eventId);
                    return null;
                });
            } catch (CalendarApiException e) {
                log.warn("Calendar event {} not deleted: {}", eventId, e.getMessage());
            }
        });
    }

    /** BK-10. Empty optional: the event is gone. Throws when the calendar can't be read. */
    Optional<CalendarApi.EventState> event(TenantId tenantId, String eventId) {
        CalendarConnectionRepository.Row c = connections.find(tenantId)
                .filter(CalendarConnectionRepository.Row::connected)
                .orElseThrow(() -> new CalendarUnavailableException("No connected calendar"));
        try {
            return withToken(tenantId, c, token -> api.getEvent(token, c.calendarId(), eventId));
        } catch (CalendarApiException e) {
            throw new CalendarUnavailableException(e.getMessage());
        }
    }

    void forget(TenantId tenantId) {
        accessTokens.invalidate(tenantId.value());
    }

    private interface TokenCall<T> {
        T run(String accessToken);
    }

    private <T> T withToken(TenantId tenantId, CalendarConnectionRepository.Row c, TokenCall<T> call) {
        String token = accessTokens.getIfPresent(tenantId.value());
        try {
            if (token == null) {
                token = api.refresh(cipher.decryptString(c.refreshToken())).accessToken();
                accessTokens.put(tenantId.value(), token);
            }
            return call.run(token);
        } catch (CalendarApiException e) {
            accessTokens.invalidate(tenantId.value());
            if (e.revoked()) {
                revoked(tenantId, e.getMessage());
            }
            throw e;
        }
    }

    /** BK-01: access was revoked; mark it and alert the Zamu team once. */
    private void revoked(TenantId tenantId, String reason) {
        if (connections.markRevoked(tenantId, reason, clock.instant()) == 0) {
            return;
        }
        log.error("Calendar access revoked for tenant {}", tenantId);
        for (String operator : users.activePlatformEmails()) {
            email.send(new EmailMessage(operator, "A client's Google Calendar needs reconnecting", """
                    Zamu can no longer read a client's Google Calendar, so bookings for them go to their staff
                    until it is reconnected.

                    Reconnect it: %s/admin/tenants/%s/setup/calendar

                    Zamu""".formatted(baseUrl, tenantId.value())));
        }
    }

    private CalendarApi.EventDetails details(Booking b, boolean tentative) {
        String who = b.customerName() == null || b.customerName().isBlank() ? "Customer" : b.customerName();
        String summary = (tentative ? "[Held] " : "") + b.serviceName() + ": " + who;
        StringBuilder description = new StringBuilder();
        description.append("Booked through Zamu (").append(b.shortCode()).append(")\n");
        description.append("Customer: ").append(who).append(", +").append(b.customerPhone()).append('\n');
        description.append("Service: ").append(b.serviceName()).append('\n');
        if (b.resourceName() != null) {
            description.append("With: ").append(b.resourceName()).append('\n');
        }
        if (b.conversationId() != null) {
            description.append("Conversation: ").append(baseUrl).append("/app/conversations/").append(b.conversationId());
        }
        return new CalendarApi.EventDetails(summary, description.toString(), b.startsAt(), b.endsAt(), tentative);
    }
}
