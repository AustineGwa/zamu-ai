package ai.zamu.reports;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import ai.zamu.bookings.BusinessHoursService;
import ai.zamu.bookings.DayHours;
import ai.zamu.shared.ids.TenantId;
import ai.zamu.tenants.TenantService;

/**
 * The owner's weekly report (REP-01, REP-02) and its CSV export (REP-05). After hours means outside the
 * business's opening hours that day (ASSUMPTION: the spec doesn't define it further).
 */
@Service
public class ReportService {

    static final int TOP = 10;
    static final int UNANSWERED = 20;

    private final ReportsRepository repository;
    private final BusinessHoursService hours;
    private final TenantService tenants;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    ReportService(ReportsRepository repository, BusinessHoursService hours, TenantService tenants,
            ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.hours = hours;
        this.tenants = tenants;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public ZoneId zone(TenantId tenantId) {
        return ZoneId.of(tenants.get(tenantId).timezone());
    }

    /** Monday of the last complete week, in the tenant's timezone. */
    public LocalDate lastCompleteWeek(TenantId tenantId) {
        return thisWeek(tenantId).minusWeeks(1);
    }

    public LocalDate thisWeek(TenantId tenantId) {
        return LocalDate.now(clock.withZone(zone(tenantId))).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    @Transactional(readOnly = true)
    public WeeklyReport weekly(TenantId tenantId, LocalDate anyDayOfWeek) {
        ZoneId zone = zone(tenantId);
        LocalDate monday = anyDayOfWeek.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Instant from = monday.atStartOfDay(zone).toInstant();
        Instant to = monday.plusWeeks(1).atStartOfDay(zone).toInstant();
        List<DayHours> week = hours.week(tenantId);
        boolean hoursConfigured = hours.configured(tenantId);

        List<Instant> openings = repository.conversationOpenings(tenantId, from, to);
        int conversations = openings.size();
        int afterHoursConversations = hoursConfigured
                ? (int) openings.stream().filter(t -> afterHours(t, zone, week)).count() : 0;
        int handedOver = repository.conversationsHandedOver(tenantId, from, to);
        int rate = conversations == 0 ? 0 : Math.round(100f * handedOver / conversations);

        List<ReportsRepository.BookingRow> made = repository.bookingsMade(tenantId, from, to);
        Map<String, Integer> byService = new LinkedHashMap<>();
        made.forEach(b -> byService.merge(b.service(), 1, Integer::sum));
        List<WeeklyReport.ServiceCount> services = byService.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(e -> new WeeklyReport.ServiceCount(e.getKey(), e.getValue())).toList();
        int afterHoursBookings = hoursConfigured
                ? (int) made.stream().filter(b -> afterHours(b.createdAt(), zone, week)).count() : 0;

        return new WeeklyReport(monday, monday.plusDays(6), conversations, afterHoursConversations, handedOver, rate,
                median(repository.firstReplyMinutes(tenantId, from, to)), services, made.size(), afterHoursBookings,
                repository.noShows(tenantId, from, to), BigDecimal.ZERO, topQuestions(tenantId, from, to),
                repository.unanswered(tenantId, from, to, UNANSWERED), hoursConfigured);
    }

    static boolean afterHours(Instant at, ZoneId zone, List<DayHours> week) {
        ZonedDateTime local = at.atZone(zone);
        DayHours day = week.get(local.getDayOfWeek().getValue() - 1);
        return day.opensAt() == null || local.toLocalTime().isBefore(day.opensAt())
                || !local.toLocalTime().isBefore(day.closesAt());
    }

    static Integer median(List<Long> values) {
        if (values.isEmpty()) {
            return null;
        }
        List<Long> sorted = values.stream().sorted().toList();
        int mid = sorted.size() / 2;
        long m = sorted.size() % 2 == 1 ? sorted.get(mid) : Math.round((sorted.get(mid - 1) + sorted.get(mid)) / 2.0);
        return (int) m;
    }

    private List<WeeklyReport.TopQuestion> topQuestions(TenantId tenantId, Instant from, Instant to) {
        Map<String, Integer> counts = new HashMap<>();
        for (String json : repository.usedEntryIds(tenantId, from, to)) {
            JsonNode ids = objectMapper.readTree(json);
            for (JsonNode id : ids) {
                counts.merge(id.asString(""), 1, Integer::sum);
            }
        }
        Map<String, String> titles = new HashMap<>();
        repository.entryTitles(tenantId).forEach(r -> titles.put(r[0], r[1]));
        List<WeeklyReport.TopQuestion> top = new ArrayList<>();
        counts.entrySet().stream()
                .filter(e -> titles.containsKey(e.getKey()))
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(TOP)
                .forEach(e -> top.add(new WeeklyReport.TopQuestion(titles.get(e.getKey()), e.getValue())));
        top.sort(Comparator.comparingInt(WeeklyReport.TopQuestion::answers).reversed()
                .thenComparing(WeeklyReport.TopQuestion::title));
        return top;
    }

    /** REP-05: the report as CSV, one metric per row. */
    public String csv(WeeklyReport r) {
        StringBuilder out = new StringBuilder("metric,value\n");
        row(out, "week_start", r.weekStart().toString());
        row(out, "week_end", r.weekEnd().toString());
        row(out, "conversations", r.conversations());
        row(out, "after_hours_conversations", r.afterHoursConversations());
        row(out, "conversations_handed_over", r.handedOver());
        row(out, "handover_rate_percent", r.handoverRatePercent());
        row(out, "median_first_reply_minutes", r.medianFirstReplyMinutes() == null ? "" : r.medianFirstReplyMinutes());
        row(out, "bookings", r.bookings());
        row(out, "after_hours_bookings", r.afterHoursBookings());
        row(out, "no_shows", r.noShows());
        row(out, "payments_collected_kes", r.paymentsCollectedKes().toPlainString());
        r.bookingsByService().forEach(s -> row(out, "bookings: " + s.service(), s.bookings()));
        r.topQuestions().forEach(q -> row(out, "answers using: " + q.title(), q.answers()));
        r.unanswered().forEach(u -> row(out, "unanswered", u.question()));
        return out.toString();
    }

    private static void row(StringBuilder out, String metric, Object value) {
        out.append(csvCell(metric)).append(',').append(csvCell(String.valueOf(value))).append('\n');
    }

    /** RFC 4180 quoting, and a leading quote for cells that spreadsheets would read as formulas. */
    static String csvCell(String value) {
        String v = value == null ? "" : value;
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
