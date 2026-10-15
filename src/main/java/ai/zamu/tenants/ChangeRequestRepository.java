package ai.zamu.tenants;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.ids.Uuids;
import ai.zamu.shared.security.CrossTenant;
import ai.zamu.shared.time.ZamuTime;

/** The change_requests table. */
@Component
class ChangeRequestRepository {

    private static final String SELECT = """
            SELECT c.id, c.tenant_id, t.display_name, c.requested_by, c.type, c.details, c.status, c.handled_by,
                   c.handled_at, c.note, c.created_at
              FROM change_requests c JOIN tenants t ON t.id = c.tenant_id
            """;

    private static final RowMapper<ChangeRequest> MAPPER = (rs, n) -> new ChangeRequest(Uuids.read(rs, "id"),
            Uuids.read(rs, "tenant_id"), rs.getString("display_name"), Uuids.read(rs, "requested_by"),
            ChangeRequestType.fromDb(rs.getString("type")), rs.getString("details"), rs.getString("status"),
            Uuids.read(rs, "handled_by"), ZamuTime.read(rs, "handled_at"), rs.getString("note"),
            ZamuTime.read(rs, "created_at"));

    private final JdbcClient jdbc;

    ChangeRequestRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void insert(TenantId tenantId, UUID id, UUID requestedBy, ChangeRequestType type, String details, Instant now) {
        jdbc.sql("""
                INSERT INTO change_requests (id, tenant_id, requested_by, type, details, status, created_at, updated_at)
                VALUES (:id, :tenantId, :requestedBy, :type, :details, 'open', :now, :now)
                """)
                .param("id", Uuids.toBytes(id))
                .param("tenantId", tenantId.bytes())
                .param("requestedBy", Uuids.toBytes(requestedBy))
                .param("type", type.dbValue())
                .param("details", details)
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    List<ChangeRequest> findForTenant(TenantId tenantId, int limit) {
        return jdbc.sql(SELECT + " WHERE c.tenant_id = :tenantId ORDER BY c.created_at DESC LIMIT :limit")
                .param("tenantId", tenantId.bytes())
                .param("limit", limit)
                .query(MAPPER)
                .list();
    }

    @CrossTenant("Zamu team change-request queue across all clients (admin console only)")
    List<ChangeRequest> findAll(boolean openOnly, int limit) {
        return jdbc.sql(SELECT + (openOnly ? " WHERE c.status = 'open'" : "")
                        + " ORDER BY c.status = 'open' DESC, c.created_at DESC LIMIT :limit")
                .param("limit", limit)
                .query(MAPPER)
                .list();
    }

    @CrossTenant("Admin console: the request ID comes from the queue page; the row carries its tenant")
    Optional<ChangeRequest> findById(UUID id) {
        return jdbc.sql(SELECT + " WHERE c.id = :id")
                .param("id", Uuids.toBytes(id))
                .query(MAPPER)
                .optional();
    }

    int close(TenantId tenantId, UUID id, String status, UUID handledBy, String note, Instant now) {
        return jdbc.sql("""
                UPDATE change_requests SET status = :status, handled_by = :handledBy, handled_at = :now, note = :note,
                       updated_at = :now
                 WHERE tenant_id = :tenantId AND id = :id AND status = 'open'
                """)
                .param("tenantId", tenantId.bytes())
                .param("id", Uuids.toBytes(id))
                .param("status", status)
                .param("handledBy", Uuids.toBytes(handledBy))
                .param("note", note)
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    @CrossTenant("Health and review counts across clients for the admin console")
    int countOpen() {
        return jdbc.sql("SELECT COUNT(*) FROM change_requests WHERE status = 'open'").query(Integer.class).single();
    }

    int deleteAllForTenant(TenantId tenantId) {
        return jdbc.sql("DELETE FROM change_requests WHERE tenant_id = :tenantId").param("tenantId", tenantId.bytes()).update();
    }
}
