package ai.zamu.knowledge;

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

/** The kb_entries table. */
@Component
class KbEntryRepository {

    private static final String COLUMNS = """
            id, tenant_id, title, question, answer_en, answer_sw, status, approved_by, approved_at, source_ref,
            created_at, updated_at""";

    private static final RowMapper<KbEntry> MAPPER = (rs, n) -> new KbEntry(
            Uuids.read(rs, "id"),
            Uuids.read(rs, "tenant_id"),
            rs.getString("title"),
            rs.getString("question"),
            rs.getString("answer_en"),
            rs.getString("answer_sw"),
            KbEntryStatus.fromDb(rs.getString("status")),
            Uuids.read(rs, "approved_by"),
            ZamuTime.read(rs, "approved_at"),
            rs.getString("source_ref"),
            ZamuTime.read(rs, "created_at"),
            ZamuTime.read(rs, "updated_at"));

    private final JdbcClient jdbc;

    KbEntryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<KbEntry> findAll(TenantId tenantId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM kb_entries WHERE tenant_id = :tenantId ORDER BY title, id")
                .param("tenantId", tenantId.bytes())
                .query(MAPPER)
                .list();
    }

    List<KbEntry> findByStatus(TenantId tenantId, KbEntryStatus status) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM kb_entries WHERE tenant_id = :tenantId AND status = :status ORDER BY title, id")
                .param("tenantId", tenantId.bytes())
                .param("status", status.dbValue())
                .query(MAPPER)
                .list();
    }

    Optional<KbEntry> findById(TenantId tenantId, UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM kb_entries WHERE tenant_id = :tenantId AND id = :id")
                .param("tenantId", tenantId.bytes())
                .param("id", Uuids.toBytes(id))
                .query(MAPPER)
                .optional();
    }

    int countByStatus(TenantId tenantId, KbEntryStatus status) {
        return jdbc.sql("SELECT COUNT(*) FROM kb_entries WHERE tenant_id = :tenantId AND status = :status")
                .param("tenantId", tenantId.bytes())
                .param("status", status.dbValue())
                .query(Integer.class)
                .single();
    }

    void insert(TenantId tenantId, UUID id, KbEntryForm form, Instant now) {
        jdbc.sql("""
                INSERT INTO kb_entries (id, tenant_id, title, question, answer_en, answer_sw, status, source_ref,
                                        created_at, updated_at)
                VALUES (:id, :tenantId, :title, :question, :answerEn, :answerSw, :status, :sourceRef, :now, :now)
                """)
                .param("id", Uuids.toBytes(id))
                .param("tenantId", tenantId.bytes())
                .param("title", form.title().strip())
                .param("question", form.question().strip())
                .param("answerEn", form.answerEn().strip())
                .param("answerSw", blankToNull(form.answerSw()))
                .param("status", KbEntryStatus.DRAFT.dbValue())
                .param("sourceRef", blankToNull(form.sourceRef()))
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    int update(TenantId tenantId, UUID id, KbEntryForm form, Instant now) {
        return jdbc.sql("""
                UPDATE kb_entries SET title = :title, question = :question, answer_en = :answerEn,
                                      answer_sw = :answerSw, source_ref = :sourceRef, updated_at = :now
                 WHERE tenant_id = :tenantId AND id = :id
                """)
                .param("tenantId", tenantId.bytes())
                .param("id", Uuids.toBytes(id))
                .param("title", form.title().strip())
                .param("question", form.question().strip())
                .param("answerEn", form.answerEn().strip())
                .param("answerSw", blankToNull(form.answerSw()))
                .param("sourceRef", blankToNull(form.sourceRef()))
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    int approve(TenantId tenantId, UUID id, UUID userId, Instant now) {
        return jdbc.sql("""
                UPDATE kb_entries SET status = :status, approved_by = :userId, approved_at = :now, updated_at = :now
                 WHERE tenant_id = :tenantId AND id = :id
                """)
                .param("tenantId", tenantId.bytes())
                .param("id", Uuids.toBytes(id))
                .param("status", KbEntryStatus.APPROVED.dbValue())
                .param("userId", userId == null ? null : Uuids.toBytes(userId))
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    int setStatus(TenantId tenantId, UUID id, KbEntryStatus status, Instant now) {
        return jdbc.sql("UPDATE kb_entries SET status = :status, updated_at = :now WHERE tenant_id = :tenantId AND id = :id")
                .param("tenantId", tenantId.bytes())
                .param("id", Uuids.toBytes(id))
                .param("status", status.dbValue())
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    int deleteAllForTenant(TenantId tenantId) {
        return jdbc.sql("DELETE FROM kb_entries WHERE tenant_id = :tenantId").param("tenantId", tenantId.bytes()).update();
    }
}
