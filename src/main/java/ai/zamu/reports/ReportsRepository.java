package ai.zamu.reports;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.ids.Uuids;
import ai.zamu.shared.security.CrossTenant;
import ai.zamu.shared.time.ZamuTime;

/**
 * Read-only report queries. They read tables owned by conversations, handovers and bookings (reports writes only
 * usage_daily and report_deliveries); aggregating in SQL keeps the reports fast without loading every row.
 */
@Component
class ReportsRepository {

    record TenantMonthUsage(UUID tenantId, int conversations, int messagesIn, int messagesOut, BigDecimal aiCostUsd,
            int waBillableMessages) {
    }

    private final JdbcClient jdbc;

    ReportsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<Instant> conversationOpenings(TenantId tenantId, Instant from, Instant to) {
        return jdbc.sql("""
                SELECT opened_at FROM conversations WHERE tenant_id = :tenantId AND opened_at >= :from AND opened_at < :to
                """)
                .param("tenantId", tenantId.bytes()).param("from", ZamuTime.toDb(from)).param("to", ZamuTime.toDb(to))
                .query((rs, n) -> ZamuTime.read(rs, "opened_at"))
                .list();
    }

    int conversationsHandedOver(TenantId tenantId, Instant from, Instant to) {
        return jdbc.sql("""
                SELECT COUNT(DISTINCT c.id) FROM conversations c JOIN handovers h ON h.conversation_id = c.id
                 WHERE c.tenant_id = :tenantId AND h.tenant_id = :tenantId AND c.opened_at >= :from AND c.opened_at < :to
                   AND h.reason NOT IN ('staff_only', 'staff_initiated')
                """)
                .param("tenantId", tenantId.bytes()).param("from", ZamuTime.toDb(from)).param("to", ZamuTime.toDb(to))
                .query(Integer.class).single();
    }

    /** Minutes from handover to first staff reply, for handovers requested in the period. */
    List<Long> firstReplyMinutes(TenantId tenantId, Instant from, Instant to) {
        return jdbc.sql("""
                SELECT requested_at, first_staff_reply_at FROM handovers
                 WHERE tenant_id = :tenantId AND requested_at >= :from AND requested_at < :to
                   AND first_staff_reply_at IS NOT NULL
                """)
                .param("tenantId", tenantId.bytes()).param("from", ZamuTime.toDb(from)).param("to", ZamuTime.toDb(to))
                .query((rs, n) -> java.time.Duration.between(ZamuTime.read(rs, "requested_at"),
                        ZamuTime.read(rs, "first_staff_reply_at")).toMinutes())
                .list();
    }

    record BookingRow(String service, Instant createdAt, String source) {
    }

    /** Bookings made in the period that weren't released unpaid or cancelled straight away. */
    List<BookingRow> bookingsMade(TenantId tenantId, Instant from, Instant to) {
        return jdbc.sql("""
                SELECT s.name, b.created_at, b.source FROM bookings b JOIN services s ON s.id = b.service_id
                 WHERE b.tenant_id = :tenantId AND b.created_at >= :from AND b.created_at < :to
                   AND NOT (b.status = 'cancelled' AND b.cancelled_by = 'system')
                """)
                .param("tenantId", tenantId.bytes()).param("from", ZamuTime.toDb(from)).param("to", ZamuTime.toDb(to))
                .query((rs, n) -> new BookingRow(rs.getString("name"), ZamuTime.read(rs, "created_at"), rs.getString("source")))
                .list();
    }

    int noShows(TenantId tenantId, Instant from, Instant to) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM bookings WHERE tenant_id = :tenantId AND status = 'no_show'
                   AND starts_at >= :from AND starts_at < :to
                """)
                .param("tenantId", tenantId.bytes()).param("from", ZamuTime.toDb(from)).param("to", ZamuTime.toDb(to))
                .query(Integer.class).single();
    }

    /** kb_entry_ids JSON of Zamu's replies in the period. */
    List<String> usedEntryIds(TenantId tenantId, Instant from, Instant to) {
        return jdbc.sql("""
                SELECT kb_entry_ids FROM messages WHERE tenant_id = :tenantId AND direction = 'out' AND sender = 'bot'
                   AND kb_entry_ids IS NOT NULL AND created_at >= :from AND created_at < :to
                """)
                .param("tenantId", tenantId.bytes()).param("from", ZamuTime.toDb(from)).param("to", ZamuTime.toDb(to))
                .query((rs, n) -> rs.getString("kb_entry_ids"))
                .list();
    }

    List<String[]> entryTitles(TenantId tenantId) {
        return jdbc.sql("SELECT id, title FROM kb_entries WHERE tenant_id = :tenantId")
                .param("tenantId", tenantId.bytes())
                .query((rs, n) -> new String[] {Uuids.read(rs, "id").toString(), rs.getString("title")})
                .list();
    }

    /** The customer's last message before each low-confidence handover in the period (REP-02). */
    List<WeeklyReport.Unanswered> unanswered(TenantId tenantId, Instant from, Instant to, int limit) {
        return jdbc.sql("""
                SELECT (SELECT m.body FROM messages m
                         WHERE m.tenant_id = h.tenant_id AND m.conversation_id = h.conversation_id
                           AND m.direction = 'in' AND m.created_at <= h.requested_at
                         ORDER BY m.created_at DESC, m.id DESC LIMIT 1) AS question,
                       h.requested_at
                  FROM handovers h
                 WHERE h.tenant_id = :tenantId AND h.reason = 'low_confidence'
                   AND h.requested_at >= :from AND h.requested_at < :to
                 ORDER BY h.requested_at DESC
                 LIMIT :limit
                """)
                .param("tenantId", tenantId.bytes()).param("from", ZamuTime.toDb(from)).param("to", ZamuTime.toDb(to))
                .param("limit", limit)
                .query((rs, n) -> new WeeklyReport.Unanswered(rs.getString("question"), ZamuTime.read(rs, "requested_at")))
                .list()
                .stream().filter(u -> u.question() != null && !u.question().isBlank()).toList();
    }

    // --- usage (REP-03) ---

    @CrossTenant("Monthly usage of every client for the Zamu admin's billing and margin report")
    List<TenantMonthUsage> monthUsage(YearMonth month) {
        return jdbc.sql("""
                SELECT tenant_id, SUM(conversations) AS conversations, SUM(messages_in) AS messages_in,
                       SUM(messages_out) AS messages_out, SUM(ai_cost_usd) AS ai_cost_usd,
                       SUM(wa_billable_messages) AS wa_billable_messages
                  FROM usage_daily
                 WHERE usage_date >= :from AND usage_date <= :to
                 GROUP BY tenant_id
                """)
                .param("from", month.atDay(1)).param("to", month.atEndOfMonth())
                .query((rs, n) -> new TenantMonthUsage(Uuids.read(rs, "tenant_id"), rs.getInt("conversations"),
                        rs.getInt("messages_in"), rs.getInt("messages_out"),
                        rs.getBigDecimal("ai_cost_usd") == null ? BigDecimal.ZERO : rs.getBigDecimal("ai_cost_usd"),
                        rs.getInt("wa_billable_messages")))
                .list();
    }

    // --- deliveries ---

    /** Claims a scheduled delivery; false when it was already sent (or claimed by another instance). */
    boolean claimDelivery(TenantId tenantId, String kind, LocalDate periodStart, Instant now) {
        try {
            return jdbc.sql("""
                    INSERT INTO report_deliveries (tenant_id, kind, period_start, sent_at)
                    VALUES (:tenantId, :kind, :period, :now)
                    """)
                    .param("tenantId", tenantId.bytes()).param("kind", kind).param("period", periodStart)
                    .param("now", ZamuTime.toDb(now))
                    .update() == 1;
        } catch (org.springframework.dao.DuplicateKeyException e) {
            return false;
        }
    }

    void releaseDelivery(TenantId tenantId, String kind, LocalDate periodStart) {
        jdbc.sql("DELETE FROM report_deliveries WHERE tenant_id = :tenantId AND kind = :kind AND period_start = :period")
                .param("tenantId", tenantId.bytes()).param("kind", kind).param("period", periodStart)
                .update();
    }
}
