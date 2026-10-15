package ai.zamu.ops;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import ai.zamu.shared.ids.Uuids;
import ai.zamu.shared.security.CrossTenant;
import ai.zamu.shared.time.ZamuTime;

/**
 * Read-only queries across tenants for the admin console's Health and Review queue screens and for operator
 * alerts. Nothing here writes; the tables belong to their feature packages.
 */
@Component
class OpsRepository {

    record JobCounts(int queued, int due, int running, int failedWaiting, int dead, Instant oldestDue) {
    }

    record DeadJob(UUID id, String type, String tenant, int attempts, String lastError, Instant updatedAt) {
    }

    record NumberQuality(String tenant, String displayPhone, String rating) {
    }

    record ReviewItem(UUID tenantId, String tenant, UUID conversationId, String kind, String detail, Instant at) {
    }

    private final JdbcClient jdbc;

    OpsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @CrossTenant("Job queue health across all tenants (admin console, NFR-04, NFR-16)")
    JobCounts jobCounts(Instant now) {
        return jdbc.sql("""
                SELECT SUM(CASE WHEN status = 'queued' THEN 1 ELSE 0 END) AS queued,
                       SUM(CASE WHEN status IN ('queued', 'failed') AND run_at <= :now THEN 1 ELSE 0 END) AS due,
                       SUM(CASE WHEN status = 'running' THEN 1 ELSE 0 END) AS running,
                       SUM(CASE WHEN status = 'failed' THEN 1 ELSE 0 END) AS failed_waiting,
                       SUM(CASE WHEN status = 'dead' THEN 1 ELSE 0 END) AS dead,
                       MIN(CASE WHEN status IN ('queued', 'failed') AND run_at <= :now THEN run_at END) AS oldest_due
                  FROM jobs
                 WHERE status IN ('queued', 'running', 'failed', 'dead')
                """)
                .param("now", ZamuTime.toDb(now))
                .query((rs, n) -> new JobCounts(rs.getInt("queued"), rs.getInt("due"), rs.getInt("running"),
                        rs.getInt("failed_waiting"), rs.getInt("dead"), ZamuTime.read(rs, "oldest_due")))
                .single();
    }

    @CrossTenant("Dead jobs across tenants, to inspect and retry (NFR-04)")
    List<DeadJob> deadJobs(int limit) {
        return jdbc.sql("""
                SELECT j.id, j.type, t.display_name, j.attempts, j.last_error, j.updated_at
                  FROM jobs j LEFT JOIN tenants t ON t.id = j.tenant_id
                 WHERE j.status = 'dead'
                 ORDER BY j.updated_at DESC LIMIT :limit
                """)
                .param("limit", limit)
                .query((rs, n) -> new DeadJob(Uuids.read(rs, "id"), rs.getString("type"), rs.getString("display_name"),
                        rs.getInt("attempts"), rs.getString("last_error"), ZamuTime.read(rs, "updated_at")))
                .list();
    }

    @CrossTenant("Count of jobs that died since a time, for the operator alert")
    int deadSince(Instant since) {
        return jdbc.sql("SELECT COUNT(*) FROM jobs WHERE status = 'dead' AND updated_at > :since")
                .param("since", ZamuTime.toDb(since))
                .query(Integer.class).single();
    }

    @CrossTenant("Webhook payloads stored but not processed, across tenants (monitoring)")
    int unprocessedWebhooks(Instant olderThan) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM webhook_events WHERE processed_at IS NULL AND tenant_id IS NOT NULL
                   AND received_at < :before
                """)
                .param("before", ZamuTime.toDb(olderThan))
                .query(Integer.class).single();
    }

    @CrossTenant("WhatsApp number quality ratings for every client (Health screen)")
    List<NumberQuality> numberQuality() {
        return jdbc.sql("""
                SELECT t.display_name, w.display_phone, w.quality_rating
                  FROM whatsapp_accounts w JOIN tenants t ON t.id = w.tenant_id
                 ORDER BY CASE w.quality_rating WHEN 'RED' THEN 0 WHEN 'YELLOW' THEN 1 ELSE 2 END, t.display_name
                """)
                .query((rs, n) -> new NumberQuality(rs.getString("display_name"), rs.getString("display_phone"),
                        rs.getString("quality_rating")))
                .list();
    }

    @CrossTenant("Clients whose calendar access was revoked (Health screen)")
    List<String> revokedCalendars() {
        return jdbc.sql("""
                SELECT t.display_name FROM calendar_connections c JOIN tenants t ON t.id = c.tenant_id
                 WHERE c.status = 'revoked' ORDER BY t.display_name
                """)
                .query(String.class).list();
    }

    @CrossTenant("Outbound messages that failed in the last day, for the Health screen")
    int failedMessagesSince(Instant since) {
        return jdbc.sql("SELECT COUNT(*) FROM messages WHERE direction = 'out' AND status = 'failed' AND updated_at > :since")
                .param("since", ZamuTime.toDb(since))
                .query(Integer.class).single();
    }

    /**
     * Review queue (spec: admin console): low-confidence answers and other handovers that weren't routine, and
     * messages that failed to send, across all clients, newest first.
     */
    @CrossTenant("Daily review across all clients by the Zamu team (admin console)")
    List<ReviewItem> reviewItems(Instant since, int limit) {
        return jdbc.sql("""
                SELECT * FROM (
                    SELECT h.tenant_id, t.display_name, h.conversation_id, h.reason AS kind,
                           CASE WHEN h.reason = 'sensitive' THEN NULL ELSE
                           (SELECT m.body FROM messages m WHERE m.tenant_id = h.tenant_id AND m.conversation_id = h.conversation_id
                               AND m.direction = 'in' AND m.created_at <= h.requested_at
                             ORDER BY m.created_at DESC, m.id DESC LIMIT 1) END AS detail,
                           h.requested_at AS at
                      FROM handovers h JOIN tenants t ON t.id = h.tenant_id
                     WHERE h.requested_at > :since AND h.reason IN ('low_confidence', 'error', 'payment_issue', 'sensitive')
                    UNION ALL
                    SELECT m.tenant_id, t.display_name, m.conversation_id, 'send_failed' AS kind, m.error_code AS detail,
                           m.updated_at AS at
                      FROM messages m JOIN tenants t ON t.id = m.tenant_id
                     WHERE m.direction = 'out' AND m.status = 'failed' AND m.updated_at > :since
                ) items
                ORDER BY at DESC LIMIT :limit
                """)
                .param("since", ZamuTime.toDb(since))
                .param("limit", limit)
                .query((rs, n) -> new ReviewItem(Uuids.read(rs, "tenant_id"), rs.getString("display_name"),
                        Uuids.read(rs, "conversation_id"), rs.getString("kind"), rs.getString("detail"),
                        ZamuTime.read(rs, "at")))
                .list();
    }
}
