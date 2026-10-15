package ai.zamu.knowledge;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.ids.Uuids;
import ai.zamu.shared.time.ZamuTime;

/** The kb_chunks table. Chunks exist only for approved entries (AI-01, AI-12). */
@Component
class KbChunkRepository {

    private final JdbcClient jdbc;

    KbChunkRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Every chunk of the tenant's approved entries, for the in-memory index. */
    List<KbChunk> findApproved(TenantId tenantId) {
        return jdbc.sql("""
                SELECT c.id, c.kb_entry_id, e.title, c.language, c.content, c.embedding, c.embedding_model
                  FROM kb_chunks c
                  JOIN kb_entries e ON e.id = c.kb_entry_id AND e.tenant_id = c.tenant_id
                 WHERE c.tenant_id = :tenantId AND e.status = 'approved'
                 ORDER BY c.id
                """)
                .param("tenantId", tenantId.bytes())
                .query((rs, n) -> new KbChunk(Uuids.read(rs, "id"), Uuids.read(rs, "kb_entry_id"),
                        rs.getString("title"), rs.getString("language"), rs.getString("content"),
                        EmbeddingCodec.decode(rs.getBytes("embedding")), rs.getString("embedding_model")))
                .list();
    }

    int deleteForEntry(TenantId tenantId, UUID entryId) {
        return jdbc.sql("DELETE FROM kb_chunks WHERE tenant_id = :tenantId AND kb_entry_id = :entryId")
                .param("tenantId", tenantId.bytes())
                .param("entryId", Uuids.toBytes(entryId))
                .update();
    }

    void insert(TenantId tenantId, UUID id, UUID entryId, String language, String content, float[] embedding,
            String embeddingModel, Instant now) {
        jdbc.sql("""
                INSERT INTO kb_chunks (id, tenant_id, kb_entry_id, language, content, embedding, embedding_model, created_at)
                VALUES (:id, :tenantId, :entryId, :language, :content, :embedding, :model, :now)
                """)
                .param("id", Uuids.toBytes(id))
                .param("tenantId", tenantId.bytes())
                .param("entryId", Uuids.toBytes(entryId))
                .param("language", language)
                .param("content", content)
                .param("embedding", EmbeddingCodec.encode(embedding))
                .param("model", embeddingModel)
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    int deleteAllForTenant(TenantId tenantId) {
        return jdbc.sql("DELETE FROM kb_chunks WHERE tenant_id = :tenantId").param("tenantId", tenantId.bytes()).update();
    }
}
