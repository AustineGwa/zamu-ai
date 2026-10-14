package ai.zamu.conversations;

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

/** The messages table. Every method is scoped to one tenant (NFR-09). */
@Component
class MessageRepository {

    private static final String COLUMNS = """
            id, tenant_id, conversation_id, direction, sender, sender_user_id, wa_message_id, type, body, media_ref,
            template_json, status, error_code, created_at, updated_at""";

    private static final RowMapper<Message> MAPPER = (rs, rowNum) -> new Message(
            Uuids.read(rs, "id"),
            Uuids.read(rs, "tenant_id"),
            Uuids.read(rs, "conversation_id"),
            MessageDirection.fromDb(rs.getString("direction")),
            MessageSender.fromDb(rs.getString("sender")),
            Uuids.read(rs, "sender_user_id"),
            rs.getString("wa_message_id"),
            MessageType.fromDb(rs.getString("type")),
            rs.getString("body"),
            rs.getString("media_ref"),
            rs.getString("template_json"),
            MessageStatus.fromDb(rs.getString("status")),
            rs.getString("error_code"),
            ZamuTime.read(rs, "created_at"),
            ZamuTime.read(rs, "updated_at"));

    private final JdbcClient jdbc;

    MessageRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void insert(TenantId tenantId, Message m) {
        jdbc.sql("""
                INSERT INTO messages (id, tenant_id, conversation_id, direction, sender, sender_user_id, wa_message_id,
                                      type, body, media_ref, template_json, status, error_code, created_at, updated_at)
                VALUES (:id, :tenantId, :conversationId, :direction, :sender, :senderUserId, :waMessageId,
                        :type, :body, :mediaRef, :templateJson, :status, :errorCode, :createdAt, :updatedAt)
                """)
                .param("id", Uuids.toBytes(m.id()))
                .param("tenantId", tenantId.bytes())
                .param("conversationId", Uuids.toBytes(m.conversationId()))
                .param("direction", m.direction().dbValue())
                .param("sender", m.sender().dbValue())
                .param("senderUserId", Uuids.toBytes(m.senderUserId()))
                .param("waMessageId", m.waMessageId())
                .param("type", m.type().dbValue())
                .param("body", m.body())
                .param("mediaRef", m.mediaRef())
                .param("templateJson", m.templateJson())
                .param("status", m.status().dbValue())
                .param("errorCode", m.errorCode())
                .param("createdAt", ZamuTime.toDb(m.createdAt()))
                .param("updatedAt", ZamuTime.toDb(m.updatedAt()))
                .update();
    }

    boolean existsByWaMessageId(TenantId tenantId, String waMessageId) {
        return jdbc.sql("SELECT COUNT(*) FROM messages WHERE tenant_id = :tenantId AND wa_message_id = :waMessageId")
                .param("tenantId", tenantId.bytes())
                .param("waMessageId", waMessageId)
                .query(Integer.class)
                .single() > 0;
    }

    Optional<Message> findById(TenantId tenantId, UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM messages WHERE tenant_id = :tenantId AND id = :id")
                .param("tenantId", tenantId.bytes())
                .param("id", Uuids.toBytes(id))
                .query(MAPPER)
                .optional();
    }

    Optional<Message> findByWaMessageId(TenantId tenantId, String waMessageId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM messages WHERE tenant_id = :tenantId AND wa_message_id = :waMessageId")
                .param("tenantId", tenantId.bytes())
                .param("waMessageId", waMessageId)
                .query(MAPPER)
                .optional();
    }

    /** The most recent messages of a conversation, oldest first. */
    List<Message> findRecent(TenantId tenantId, UUID conversationId, int limit) {
        List<Message> newestFirst = jdbc.sql("SELECT " + COLUMNS + """
                 FROM messages
                WHERE tenant_id = :tenantId AND conversation_id = :conversationId
                ORDER BY created_at DESC, id DESC
                LIMIT :limit
                """)
                .param("tenantId", tenantId.bytes())
                .param("conversationId", Uuids.toBytes(conversationId))
                .param("limit", limit)
                .query(MAPPER)
                .list();
        return newestFirst.reversed();
    }

    int markSent(TenantId tenantId, UUID id, String waMessageId, Instant now) {
        return jdbc.sql("""
                UPDATE messages SET status = 'sent', wa_message_id = :waMessageId, updated_at = :now
                 WHERE tenant_id = :tenantId AND id = :id AND status = 'queued'
                """)
                .param("tenantId", tenantId.bytes())
                .param("id", Uuids.toBytes(id))
                .param("waMessageId", waMessageId)
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    int updateStatus(TenantId tenantId, UUID id, MessageStatus status, String errorCode, Instant now) {
        return jdbc.sql("""
                UPDATE messages SET status = :status, error_code = :errorCode, updated_at = :now
                 WHERE tenant_id = :tenantId AND id = :id
                """)
                .param("tenantId", tenantId.bytes())
                .param("id", Uuids.toBytes(id))
                .param("status", status.dbValue())
                .param("errorCode", errorCode)
                .param("now", ZamuTime.toDb(now))
                .update();
    }
}
