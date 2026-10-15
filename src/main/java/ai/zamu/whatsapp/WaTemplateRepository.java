package ai.zamu.whatsapp;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.ids.Uuids;
import ai.zamu.shared.time.ZamuTime;

/** The wa_templates table. */
@Component
class WaTemplateRepository {

    private static final String COLUMNS = """
            id, tenant_id, name, category, language, body, meta_status, meta_reason, created_at, updated_at""";

    private static final RowMapper<WaTemplate> MAPPER = (rs, rowNum) -> new WaTemplate(
            Uuids.read(rs, "id"),
            Uuids.read(rs, "tenant_id"),
            rs.getString("name"),
            rs.getString("category"),
            rs.getString("language"),
            rs.getString("body"),
            WaTemplateStatus.fromDb(rs.getString("meta_status")),
            rs.getString("meta_reason"),
            ZamuTime.read(rs, "created_at"),
            ZamuTime.read(rs, "updated_at"));

    private final JdbcClient jdbc;

    WaTemplateRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<WaTemplate> findAll(TenantId tenantId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM wa_templates WHERE tenant_id = :tenantId ORDER BY name, language")
                .param("tenantId", tenantId.bytes())
                .query(MAPPER)
                .list();
    }

    List<WaTemplate> findByStatus(TenantId tenantId, WaTemplateStatus status) {
        return jdbc.sql("SELECT " + COLUMNS + """
                 FROM wa_templates WHERE tenant_id = :tenantId AND meta_status = :status ORDER BY name, language""")
                .param("tenantId", tenantId.bytes())
                .param("status", status.dbValue())
                .query(MAPPER)
                .list();
    }

    Optional<WaTemplate> findById(TenantId tenantId, UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM wa_templates WHERE tenant_id = :tenantId AND id = :id")
                .param("tenantId", tenantId.bytes())
                .param("id", Uuids.toBytes(id))
                .query(MAPPER)
                .optional();
    }

    boolean exists(TenantId tenantId, String name, String language) {
        return jdbc.sql("SELECT COUNT(*) FROM wa_templates WHERE tenant_id = :tenantId AND name = :name AND language = :language")
                .param("tenantId", tenantId.bytes())
                .param("name", name)
                .param("language", language)
                .query(Integer.class)
                .single() > 0;
    }

    void insert(TenantId tenantId, UUID id, WaTemplateForm form, Instant now) {
        jdbc.sql("""
                INSERT INTO wa_templates (id, tenant_id, name, category, language, body, meta_status, created_at, updated_at)
                VALUES (:id, :tenantId, :name, :category, :language, :body, :status, :now, :now)
                """)
                .param("id", Uuids.toBytes(id))
                .param("tenantId", tenantId.bytes())
                .param("name", form.name())
                .param("category", form.category())
                .param("language", form.language())
                .param("body", form.body())
                .param("status", WaTemplateStatus.DRAFT.dbValue())
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    int updateStatus(TenantId tenantId, UUID id, WaTemplateStatus status, String reason, Instant now) {
        return jdbc.sql("""
                UPDATE wa_templates SET meta_status = :status, meta_reason = :reason, updated_at = :now
                 WHERE tenant_id = :tenantId AND id = :id
                """)
                .param("tenantId", tenantId.bytes())
                .param("id", Uuids.toBytes(id))
                .param("status", status.dbValue())
                .param("reason", reason)
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    int updateStatusByName(TenantId tenantId, String name, String language, WaTemplateStatus status, String reason,
            Instant now) {
        return jdbc.sql("""
                UPDATE wa_templates SET meta_status = :status, meta_reason = :reason, updated_at = :now
                 WHERE tenant_id = :tenantId AND name = :name AND language = :language
                """)
                .param("tenantId", tenantId.bytes())
                .param("name", name)
                .param("language", language)
                .param("status", status.dbValue())
                .param("reason", reason)
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    int deleteAllForTenant(TenantId tenantId) {
        return jdbc.sql("DELETE FROM wa_templates WHERE tenant_id = :tenantId").param("tenantId", tenantId.bytes()).update();
    }
}
