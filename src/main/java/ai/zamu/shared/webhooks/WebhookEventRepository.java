package ai.zamu.shared.webhooks;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.ids.Uuids;
import ai.zamu.shared.security.CrossTenant;
import ai.zamu.shared.time.ZamuTime;

/** The webhook_events table: raw provider payloads, stored before any processing (MSG-01, PAY-05). */
@Component
class WebhookEventRepository {

    private final JdbcClient jdbc;

    WebhookEventRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @CrossTenant("Raw payloads are stored before, or without, a tenant being identified (MSG-03)")
    void insert(UUID id, WebhookSource source, TenantId tenantId, String rawPayload, Instant receivedAt) {
        jdbc.sql("""
                INSERT INTO webhook_events (id, source, tenant_id, raw_payload, received_at)
                VALUES (:id, :source, :tenantId, :payload, :receivedAt)
                """)
                .param("id", Uuids.toBytes(id))
                .param("source", source.dbValue())
                .param("tenantId", tenantId == null ? null : tenantId.bytes())
                .param("payload", rawPayload)
                .param("receivedAt", ZamuTime.toDb(receivedAt))
                .update();
    }

    Optional<String> findPayload(TenantId tenantId, UUID id) {
        return jdbc.sql("SELECT raw_payload FROM webhook_events WHERE id = :id AND tenant_id = :tenantId")
                .param("id", Uuids.toBytes(id))
                .param("tenantId", tenantId.bytes())
                .query(String.class)
                .optional();
    }

    int markProcessed(TenantId tenantId, UUID id, Instant now) {
        return jdbc.sql("UPDATE webhook_events SET processed_at = :now WHERE id = :id AND tenant_id = :tenantId")
                .param("id", Uuids.toBytes(id))
                .param("tenantId", tenantId.bytes())
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    @CrossTenant("Retention housekeeping across all tenants: raw payloads are kept 30 days")
    int deleteReceivedBefore(Instant cutoff) {
        return jdbc.sql("DELETE FROM webhook_events WHERE received_at < :cutoff")
                .param("cutoff", ZamuTime.toDb(cutoff))
                .update();
    }
}
