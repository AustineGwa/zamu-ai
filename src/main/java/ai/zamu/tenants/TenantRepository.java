package ai.zamu.tenants;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.ids.Uuids;
import ai.zamu.shared.security.CrossTenant;
import ai.zamu.shared.time.ZamuTime;

/** The tenants table. Every method that reads or changes one tenant takes its {@link TenantId} (NFR-09). */
@Component
class TenantRepository {

    private final JdbcClient jdbc;

    TenantRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @CrossTenant("Creates a brand-new tenant; admin console only")
    void insert(Tenant tenant) {
        jdbc.sql("""
                INSERT INTO tenants (id, name, display_name, plan, status, timezone, default_language, tone,
                                     conversation_limit, confidence_threshold, created_at, updated_at)
                VALUES (:id, :name, :displayName, :plan, :status, :timezone, :defaultLanguage, :tone,
                        :conversationLimit, :confidenceThreshold, :createdAt, :updatedAt)
                """)
                .param("id", Uuids.toBytes(tenant.id()))
                .param("name", tenant.name())
                .param("displayName", tenant.displayName())
                .param("plan", tenant.plan().dbValue())
                .param("status", tenant.status().dbValue())
                .param("timezone", tenant.timezone())
                .param("defaultLanguage", tenant.defaultLanguage().dbValue())
                .param("tone", tenant.tone().dbValue())
                .param("conversationLimit", tenant.conversationLimit())
                .param("confidenceThreshold", tenant.confidenceThreshold())
                .param("createdAt", ZamuTime.toDb(tenant.createdAt()))
                .param("updatedAt", ZamuTime.toDb(tenant.updatedAt()))
                .update();
    }

    Optional<Tenant> findById(TenantId tenantId) {
        return jdbc.sql("SELECT " + TenantRowMapper.COLUMNS + " FROM tenants WHERE id = :id")
                .param("id", tenantId.bytes())
                .query(TenantRowMapper.INSTANCE)
                .optional();
    }

    @CrossTenant("admin console tenant list; operator and admin only")
    List<Tenant> findAll() {
        return jdbc.sql("SELECT " + TenantRowMapper.COLUMNS + " FROM tenants ORDER BY display_name, id")
                .query(TenantRowMapper.INSTANCE)
                .list();
    }

    @CrossTenant("Uniqueness check on the registered name across tenants, before a tenant exists or is renamed")
    boolean existsByNameExcluding(String name, UUID excludeId) {
        // Two statements rather than ":excludeId IS NULL", which H2 can't type when the value is null.
        Integer count = excludeId == null
                ? jdbc.sql("SELECT COUNT(*) FROM tenants WHERE name = :name")
                        .param("name", name)
                        .query(Integer.class)
                        .single()
                : jdbc.sql("SELECT COUNT(*) FROM tenants WHERE name = :name AND id <> :excludeId")
                        .param("name", name)
                        .param("excludeId", Uuids.toBytes(excludeId))
                        .query(Integer.class)
                        .single();
        return count != null && count > 0;
    }

    int updateProfile(TenantId tenantId, String name, String displayName, TenantPlan plan, int conversationLimit,
            Instant now) {
        return jdbc.sql("""
                UPDATE tenants SET name = :name, display_name = :displayName, plan = :plan,
                                   conversation_limit = :conversationLimit, updated_at = :now
                 WHERE id = :id
                """)
                .param("id", tenantId.bytes())
                .param("name", name)
                .param("displayName", displayName)
                .param("plan", plan.dbValue())
                .param("conversationLimit", conversationLimit)
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    int updateStatus(TenantId tenantId, TenantStatus status, Instant now) {
        return jdbc.sql("UPDATE tenants SET status = :status, updated_at = :now WHERE id = :id")
                .param("id", tenantId.bytes())
                .param("status", status.dbValue())
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    int updateHoldingReply(TenantId tenantId, String holdingReply, Instant now) {
        return jdbc.sql("UPDATE tenants SET holding_reply = :holdingReply, updated_at = :now WHERE id = :id")
                .param("id", tenantId.bytes())
                .param("holdingReply", holdingReply)
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    int updateTone(TenantId tenantId, TenantTone tone, TenantLanguage language, Instant now) {
        return jdbc.sql("""
                UPDATE tenants SET tone = :tone, default_language = :language, updated_at = :now
                 WHERE id = :id
                """)
                .param("id", tenantId.bytes())
                .param("tone", tone.dbValue())
                .param("language", language.dbValue())
                .param("now", ZamuTime.toDb(now))
                .update();
    }
}
