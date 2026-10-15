package ai.zamu.reports;

import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import ai.zamu.audit.AuditEvent;
import ai.zamu.audit.AuditService;

/** Admin console, Usage and billing (REP-03, REP-04, REP-05). Admins only (SecurityConfig). */
@Controller
class UsageController {

    private final UsageReportService usage;
    private final AuditService audit;

    UsageController(UsageReportService usage, AuditService audit) {
        this.usage = usage;
        this.audit = audit;
    }

    @GetMapping("/admin/usage")
    String page(@RequestParam(required = false) String month, Model model) {
        YearMonth m = parse(month);
        List<UsageReportService.UsageRow> rows = usage.month(m);
        model.addAttribute("month", m);
        model.addAttribute("previous", m.minusMonths(1));
        model.addAttribute("next", m.isBefore(usage.currentMonth()) ? m.plusMonths(1) : null);
        model.addAttribute("rows", rows);
        model.addAttribute("flagged", rows.stream().filter(UsageReportService.UsageRow::flagged).count());
        return "reports/usage";
    }

    @GetMapping("/admin/usage/export.csv")
    ResponseEntity<byte[]> csv(@RequestParam(required = false) String month) {
        YearMonth m = parse(month);
        audit.record(AuditEvent.of(null, "usage.exported", "usage", null).withAfter(java.util.Map.of("month", m.toString())));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"zamu-usage-" + m + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(usage.csv(m, usage.month(m)).getBytes(StandardCharsets.UTF_8));
    }

    private YearMonth parse(String value) {
        try {
            return value == null || value.isBlank() ? usage.currentMonth() : YearMonth.parse(value);
        } catch (DateTimeParseException e) {
            return usage.currentMonth();
        }
    }
}
