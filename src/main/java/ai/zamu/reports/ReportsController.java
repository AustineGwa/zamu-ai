package ai.zamu.reports;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import ai.zamu.audit.AuditEvent;
import ai.zamu.audit.AuditService;
import ai.zamu.shared.ids.TenantId;

/** Owner reports (spec: Reports screen; REP-01, REP-02, REP-05). Owners only (SecurityConfig). */
@Controller
class ReportsController {

    private final ReportService reports;
    private final AuditService audit;

    ReportsController(ReportService reports, AuditService audit) {
        this.reports = reports;
        this.audit = audit;
    }

    @GetMapping("/app/reports")
    String weekly(TenantId tenantId, @RequestParam(required = false) String week, Model model) {
        LocalDate thisWeek = reports.thisWeek(tenantId);
        LocalDate chosen = parse(week, reports.lastCompleteWeek(tenantId));
        if (chosen.isAfter(thisWeek)) {
            chosen = thisWeek;
        }
        WeeklyReport report = reports.weekly(tenantId, chosen);
        model.addAttribute("report", report);
        model.addAttribute("previous", report.weekStart().minusWeeks(1));
        model.addAttribute("next", report.weekStart().plusWeeks(1).isAfter(thisWeek) ? null : report.weekStart().plusWeeks(1));
        model.addAttribute("current", report.weekStart().equals(thisWeek));
        model.addAttribute("maxBookings", report.bookingsByService().stream().mapToInt(WeeklyReport.ServiceCount::bookings).max().orElse(0));
        model.addAttribute("maxAnswers", report.topQuestions().stream().mapToInt(WeeklyReport.TopQuestion::answers).max().orElse(0));
        return "reports/weekly";
    }

    /** REP-05. NFR-14: exports are audited. */
    @GetMapping("/app/reports/weekly.csv")
    ResponseEntity<byte[]> csv(TenantId tenantId, @RequestParam(required = false) String week) {
        WeeklyReport report = reports.weekly(tenantId, parse(week, reports.lastCompleteWeek(tenantId)));
        audit.record(AuditEvent.of(tenantId, "report.exported", "tenant", tenantId.value()));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"zamu-week-" + report.weekStart() + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(reports.csv(report).getBytes(StandardCharsets.UTF_8));
    }

    private static LocalDate parse(String value, LocalDate fallback) {
        try {
            return value == null || value.isBlank() ? fallback : LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return fallback;
        }
    }
}
