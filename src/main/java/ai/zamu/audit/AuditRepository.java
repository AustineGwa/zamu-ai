package ai.zamu.audit;

import java.util.List;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.ids.Uuids;
import ai.zamu.shared.security.CrossTenant;
import ai.zamu.shared.time.ZamuTime;

/** Append-only writes to audit_logs; there is no update or delete. */
@Component
class AuditRepository {

    private static final RowMapper<AuditEntry> ROW_MAPPER = (rs, rowNum) -> new AuditEntry(
            Uuids.read(rs, "id"),
            Uuids.read(rs, "tenant_id"),
            Uuids.read(rs, "actor_user_id"),
            rs.getString("action"),
            rs.getString("entity"),
            Uuids.read(rs, "entity_id"),
            rs.getString("before_state"),
            rs.getString("after_state"),
            rs.getString("ip"),
            ZamuTime.read(rs, "created_at"));

    private final JdbcClient jdbc;

    AuditRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @CrossTenant("Append-only insert; the row carries its own tenant_id, which may be null for platform actions")
    void insert(AuditEntry entry) {
        jdbc.sql("""
                INSERT INTO audit_logs (id, tenant_id, actor_user_id, action, entity, entity_id,
                                        before_state, after_state, ip, created_at)
                VALUES (:id, :tenantId, :actorUserId, :action, :entity, :entityId,
                        :beforeState, :afterState, :ip, :createdAt)
                """)
                .param("id", Uuids.toBytes(entry.id()))
                .param("tenantId", Uuids.toBytes(entry.tenantId()))
                .param("actorUserId", Uuids.toBytes(entry.actorUserId()))
                .param("action", entry.action())
                .param("entity", entry.entity())
                .param("entityId", Uuids.toBytes(entry.entityId()))
                .param("beforeState", entry.beforeJson())
                .param("afterState", entry.afterJson())
                .param("ip", entry.ip())
                .param("createdAt", ZamuTime.toDb(entry.createdAt()))
                .update();
    }

    /** Most recent entries for one tenant (owner's "own tenant" view; admin audit search comes in Milestone 7). */
    List<AuditEntry> findRecentForTenant(TenantId tenantId, int limit) {
        return jdbc.sql("""
                SELECT id, tenant_id, actor_user_id, action, entity, entity_id, before_state, after_state, ip, created_at
                  FROM audit_logs
                 WHERE tenant_id = :tenantId
                 ORDER BY created_at DESC, id DESC
                 LIMIT :limit
                """)
                .param("tenantId", tenantId.bytes())
                .param("limit", limit)
                .query(ROW_MAPPER)
                .list();
    }
}
