package ai.zamu.ops;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import ai.zamu.audit.AuditEvent;
import ai.zamu.audit.AuditSearchRow;
import ai.zamu.audit.AuditService;
import ai.zamu.shared.ids.Uuids;
import ai.zamu.shared.time.ZamuTime;
import ai.zamu.tenants.TenantService;

/** Admin console, Audit log (spec: admin console screens; NFR-14). Admins only (SecurityConfig). */
@Controller
class AuditLogController {

    private final AuditService audit;
    private final TenantService tenants;
    private final Clock clock;

    AuditLogController(AuditService audit, TenantService tenants, Clock clock) {
        this.audit = audit;
        this.tenants = tenants;
        this.clock = clock;
    }

    @GetMapping("/admin/audit")
    String search(@RequestParam(required = false) String tenant, @RequestParam(required = false) String action,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to, Model model) {
        LocalDate today = LocalDate.now(clock.withZone(ZamuTime.NAIROBI));
        LocalDate start = parse(from, today.minusDays(6));
        LocalDate end = parse(to, today);
        model.addAttribute("rows", rows(tenant, action, start, end));
        model.addAttribute("tenants", tenants.listAll());
        model.addAttribute("tenant", tenant);
        model.addAttribute("action", action);
        model.addAttribute("from", start);
        model.addAttribute("to", end);
        return "audit/log";
    }

    @GetMapping("/admin/audit/export.csv")
    ResponseEntity<byte[]> csv(@RequestParam(required = false) String tenant, @RequestParam(required = false) String action,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        LocalDate today = LocalDate.now(clock.withZone(ZamuTime.NAIROBI));
        StringBuilder out = new StringBuilder("time,client,actor,action,entity,entity_id,ip\n");
        for (AuditSearchRow r : rows(tenant, action, parse(from, today.minusDays(6)), parse(to, today))) {
            out.append(r.createdAt()).append(',').append(cell(r.tenant())).append(',').append(cell(r.actor())).append(',')
                    .append(cell(r.action())).append(',').append(cell(r.entity())).append(',')
                    .append(r.entityId() == null ? "" : r.entityId()).append(',').append(cell(r.ip())).append('\n');
        }
        audit.record(AuditEvent.of(null, "audit.exported", "audit_log", null));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"zamu-audit.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(out.toString().getBytes(StandardCharsets.UTF_8));
    }

    private List<AuditSearchRow> rows(String tenant, String action, LocalDate from, LocalDate to) {
        UUID tenantId = Uuids.parseOrNull(tenant);
        Instant start = from.atStartOfDay(ZamuTime.NAIROBI).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(ZamuTime.NAIROBI).toInstant();
        return audit.search(tenantId, action, start, end);
    }

    private static String cell(String v) {
        if (v == null) {
            return "";
        }
        String s = !v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0 ? "'" + v : v;
        return s.contains(",") || s.contains("\"") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    private static LocalDate parse(String value, LocalDate fallback) {
        try {
            return value == null || value.isBlank() ? fallback : LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return fallback;
        }
    }
}
