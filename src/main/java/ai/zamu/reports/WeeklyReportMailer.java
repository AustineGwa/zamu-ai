package ai.zamu.reports;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.mail.EmailMessage;
import ai.zamu.shared.mail.EmailSender;
import ai.zamu.tenants.TenantService;
import ai.zamu.tenants.TenantStatus;
import ai.zamu.tenants.TenantView;
import ai.zamu.users.UserService;

/**
 * REP-01, NOT-03: the weekly report by email to the owners every Monday from 8:00am in the business's timezone.
 * Each week is claimed in report_deliveries first, so it goes out once even with several instances; a later run
 * that same week catches up if the app was down at 8:00.
 */
@Component
class WeeklyReportMailer {

    private static final Logger log = LoggerFactory.getLogger(WeeklyReportMailer.class);
    static final String KIND = "weekly_report";
    static final LocalTime SEND_AT = LocalTime.of(8, 0);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);

    private final ReportService reports;
    private final ReportsRepository repository;
    private final TenantService tenants;
    private final UserService users;
    private final EmailSender email;
    private final Clock clock;
    private final String baseUrl;
    private final boolean enabled;

    WeeklyReportMailer(ReportService reports, ReportsRepository repository, TenantService tenants, UserService users,
            EmailSender email, Clock clock, @Value("${zamu.base-url}") String baseUrl,
            @Value("${zamu.jobs.enabled:true}") boolean enabled) {
        this.reports = reports;
        this.repository = repository;
        this.tenants = tenants;
        this.users = users;
        this.email = email;
        this.clock = clock;
        this.baseUrl = baseUrl;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelay = 900_000, initialDelay = 90_000)
    void scheduled() {
        if (!enabled) {
            return;
        }
        try {
            sendDue();
        } catch (RuntimeException e) {
            log.error("Weekly report run failed", e);
        }
    }

    /** Returns how many tenants' reports were sent. */
    int sendDue() {
        int sent = 0;
        for (TenantView tenant : tenants.listAll()) {
            if (tenant.status() == TenantStatus.OFFBOARDED) {
                continue;
            }
            TenantId id = TenantId.of(tenant.id());
            ZoneId zone = ZoneId.of(tenant.timezone());
            ZonedDateTime now = clock.instant().atZone(zone);
            ZonedDateTime due = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    .atTime(SEND_AT).atZone(zone);
            if (now.isBefore(due)) {
                continue;
            }
            LocalDate week = due.toLocalDate().minusWeeks(1);
            List<String> owners = users.activeOwnerEmails(id);
            if (owners.isEmpty() || !repository.claimDelivery(id, KIND, week, clock.instant())) {
                continue;
            }
            try {
                WeeklyReport r = reports.weekly(id, week);
                for (String owner : owners) {
                    email.send(new EmailMessage(owner, "Your Zamu week: " + DAY.format(r.weekStart()) + " to "
                            + DAY.format(r.weekEnd()), body(tenant, r)));
                }
                sent++;
            } catch (RuntimeException e) {
                repository.releaseDelivery(id, KIND, week); // try again on the next run
                log.warn("Weekly report for tenant {} not sent: {}", id, e.getMessage());
            }
        }
        return sent;
    }

    private String body(TenantView tenant, WeeklyReport r) {
        StringBuilder b = new StringBuilder();
        b.append("Here is how Zamu did for ").append(tenant.displayName()).append(" last week (")
                .append(DAY.format(r.weekStart())).append(" to ").append(DAY.format(r.weekEnd())).append(").\n\n");
        b.append("Conversations: ").append(r.conversations());
        if (r.hoursConfigured()) {
            b.append(" (").append(r.afterHoursConversations()).append(" after hours)");
        }
        b.append('\n');
        b.append("Handed to your team: ").append(r.handoverRatePercent()).append("%");
        if (r.medianFirstReplyMinutes() != null) {
            b.append(", first reply in ").append(r.medianFirstReplyMinutes()).append(" minutes (median)");
        }
        b.append('\n');
        b.append("Bookings: ").append(r.bookings());
        if (r.hoursConfigured() && r.bookings() > 0) {
            b.append(" (").append(r.afterHoursBookings()).append(" made after hours)");
        }
        b.append('\n');
        r.bookingsByService().forEach(s -> b.append("  ").append(s.service()).append(": ").append(s.bookings()).append('\n'));
        b.append("No-shows: ").append(r.noShows()).append('\n');
        if (!r.unanswered().isEmpty()) {
            b.append("\nQuestions Zamu couldn't answer: ").append(r.unanswered().size())
                    .append(". Add the answers so Zamu can reply next time.\n");
        }
        b.append("\nFull report: ").append(baseUrl).append("/app/reports?week=").append(r.weekStart()).append("\n\nZamu");
        return b.toString();
    }
}
