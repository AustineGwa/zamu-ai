package ai.zamu.handovers;

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

/** The handovers table. */
@Component
class HandoverRepository {

    private static final String COLUMNS = """
            id, tenant_id, conversation_id, reason, requested_at, accepted_by, accepted_at, first_staff_reply_at,
            escalated_at, resolved_at""";

    private static final RowMapper<Handover> MAPPER = (rs, rowNum) -> new Handover(
            Uuids.read(rs, "id"),
            Uuids.read(rs, "tenant_id"),
            Uuids.read(rs, "conversation_id"),
            HandoverReason.fromDb(rs.getString("reason")),
            ZamuTime.read(rs, "requested_at"),
            Uuids.read(rs, "accepted_by"),
            ZamuTime.read(rs, "accepted_at"),
            ZamuTime.read(rs, "first_staff_reply_at"),
            ZamuTime.read(rs, "escalated_at"),
            ZamuTime.read(rs, "resolved_at"));

    private final JdbcClient jdbc;

    HandoverRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<Handover> findOpen(TenantId tenantId, UUID conversationId) {
        return jdbc.sql("SELECT " + COLUMNS + """
                 FROM handovers
                WHERE tenant_id = :tenantId AND conversation_id = :conversationId AND resolved_at IS NULL
                ORDER BY requested_at DESC
                LIMIT 1
                """)
                .param("tenantId", tenantId.bytes())
                .param("conversationId", Uuids.toBytes(conversationId))
                .query(MAPPER)
                .optional();
    }

    void insert(TenantId tenantId, UUID id, UUID conversationId, HandoverReason reason, Instant now, UUID acceptedBy) {
        jdbc.sql("""
                INSERT INTO handovers (id, tenant_id, conversation_id, reason, requested_at, accepted_by, accepted_at,
                                       created_at, updated_at)
                VALUES (:id, :tenantId, :conversationId, :reason, :now, :acceptedBy, :acceptedAt, :now, :now)
                """)
                .param("id", Uuids.toBytes(id))
                .param("tenantId", tenantId.bytes())
                .param("conversationId", Uuids.toBytes(conversationId))
                .param("reason", reason.dbValue())
                .param("now", ZamuTime.toDb(now))
                .param("acceptedBy", Uuids.toBytes(acceptedBy))
                .param("acceptedAt", acceptedBy == null ? null : ZamuTime.toDb(now))
                .update();
    }

    int accept(TenantId tenantId, UUID id, UUID userId, Instant now) {
        return jdbc.sql("""
                UPDATE handovers SET accepted_by = :userId, accepted_at = :now, updated_at = :now
                 WHERE tenant_id = :tenantId AND id = :id AND resolved_at IS NULL
                """)
                .param("tenantId", tenantId.bytes())
                .param("id", Uuids.toBytes(id))
                .param("userId", Uuids.toBytes(userId))
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    int resolveOpen(TenantId tenantId, UUID conversationId, Instant now) {
        return jdbc.sql("""
                UPDATE handovers SET resolved_at = :now, updated_at = :now
                 WHERE tenant_id = :tenantId AND conversation_id = :conversationId AND resolved_at IS NULL
                """)
                .param("tenantId", tenantId.bytes())
                .param("conversationId", Uuids.toBytes(conversationId))
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    void recordFirstStaffReply(TenantId tenantId, UUID conversationId, Instant at) {
        jdbc.sql("""
                UPDATE handovers SET first_staff_reply_at = :at, updated_at = :at
                 WHERE tenant_id = :tenantId AND conversation_id = :conversationId AND resolved_at IS NULL
                   AND first_staff_reply_at IS NULL
                """)
                .param("tenantId", tenantId.bytes())
                .param("conversationId", Uuids.toBytes(conversationId))
                .param("at", ZamuTime.toDb(at))
                .update();
    }

    int countOpen(TenantId tenantId) {
        return jdbc.sql("SELECT COUNT(*) FROM handovers WHERE tenant_id = :tenantId AND resolved_at IS NULL")
                .param("tenantId", tenantId.bytes())
                .query(Integer.class)
                .single();
    }

    @CrossTenant("HO-06 escalation sweep across all tenants; each candidate is then handled within its own tenant")
    List<Handover> findEscalationCandidates(Instant requestedBefore, int limit) {
        return jdbc.sql("SELECT " + COLUMNS + """
                 FROM handovers
                WHERE resolved_at IS NULL AND escalated_at IS NULL AND first_staff_reply_at IS NULL
                  AND requested_at < :before
                ORDER BY requested_at
                LIMIT :limit
                """)
                .param("before", ZamuTime.toDb(requestedBefore))
                .param("limit", limit)
                .query(MAPPER)
                .list();
    }

    /** Marks a handover escalated; returns false if another instance already did (claim once). */
    boolean claimEscalation(TenantId tenantId, UUID id, Instant now) {
        return jdbc.sql("""
                UPDATE handovers SET escalated_at = :now, updated_at = :now
                 WHERE tenant_id = :tenantId AND id = :id AND escalated_at IS NULL AND resolved_at IS NULL
                """)
                .param("tenantId", tenantId.bytes())
                .param("id", Uuids.toBytes(id))
                .param("now", ZamuTime.toDb(now))
                .update() == 1;
    }

    // --- privacy (NFR-11, NFR-12) ---

    int deleteForConversations(TenantId tenantId, java.util.List<UUID> conversationIds) {
        int deleted = 0;
        for (UUID id : conversationIds) {
            deleted += jdbc.sql("DELETE FROM handovers WHERE tenant_id = :tenantId AND conversation_id = :conversationId")
                    .param("tenantId", tenantId.bytes())
                    .param("conversationId", ai.zamu.shared.ids.Uuids.toBytes(id))
                    .update();
        }
        return deleted;
    }

    java.util.List<java.util.Map<String, Object>> exportForConversations(TenantId tenantId, java.util.List<UUID> conversationIds) {
        java.util.List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
        for (UUID id : conversationIds) {
            jdbc.sql("""
                    SELECT conversation_id, reason, requested_at, accepted_at, resolved_at FROM handovers
                     WHERE tenant_id = :tenantId AND conversation_id = :conversationId ORDER BY requested_at
                    """)
                    .param("tenantId", tenantId.bytes())
                    .param("conversationId", ai.zamu.shared.ids.Uuids.toBytes(id))
                    .query((rs, n) -> {
                        java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
                        row.put("conversation_id", ai.zamu.shared.ids.Uuids.read(rs, "conversation_id").toString());
                        row.put("reason", rs.getString("reason"));
                        row.put("requested_at", String.valueOf(ai.zamu.shared.time.ZamuTime.read(rs, "requested_at")));
                        row.put("accepted_at", String.valueOf(ai.zamu.shared.time.ZamuTime.read(rs, "accepted_at")));
                        row.put("resolved_at", String.valueOf(ai.zamu.shared.time.ZamuTime.read(rs, "resolved_at")));
                        return row;
                    })
                    .list()
                    .forEach(out::add);
        }
        return out;
    }

    int deleteAllForTenant(TenantId tenantId) {
        return jdbc.sql("DELETE FROM handovers WHERE tenant_id = :tenantId").param("tenantId", tenantId.bytes()).update();
    }
}
