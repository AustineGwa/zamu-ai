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

/** The conversations table. Every method is scoped to one tenant (NFR-09). */
@Component
class ConversationRepository {

    private static final String COLUMNS = """
            id, tenant_id, customer_id, channel, status, assigned_user_id, opened_at, last_message_at,
            last_customer_message_at, window_expires_at, billable_session_start, unread_count, holding_reply_sent,
            closed_at, created_at, updated_at""";

    private static final RowMapper<Conversation> MAPPER = (rs, rowNum) -> new Conversation(
            Uuids.read(rs, "id"),
            Uuids.read(rs, "tenant_id"),
            Uuids.read(rs, "customer_id"),
            Channel.fromDb(rs.getString("channel")),
            ConversationStatus.fromDb(rs.getString("status")),
            Uuids.read(rs, "assigned_user_id"),
            ZamuTime.read(rs, "opened_at"),
            ZamuTime.read(rs, "last_message_at"),
            ZamuTime.read(rs, "last_customer_message_at"),
            ZamuTime.read(rs, "window_expires_at"),
            ZamuTime.read(rs, "billable_session_start"),
            rs.getInt("unread_count"),
            rs.getBoolean("holding_reply_sent"),
            ZamuTime.read(rs, "closed_at"),
            ZamuTime.read(rs, "created_at"),
            ZamuTime.read(rs, "updated_at"));

    private final JdbcClient jdbc;

    ConversationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<Conversation> findById(TenantId tenantId, UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM conversations WHERE tenant_id = :tenantId AND id = :id")
                .param("tenantId", tenantId.bytes())
                .param("id", Uuids.toBytes(id))
                .query(MAPPER)
                .optional();
    }

    Optional<Conversation> findLatest(TenantId tenantId, UUID customerId, Channel channel) {
        return jdbc.sql("SELECT " + COLUMNS + """
                 FROM conversations
                WHERE tenant_id = :tenantId AND customer_id = :customerId AND channel = :channel
                ORDER BY opened_at DESC, id DESC
                LIMIT 1
                """)
                .param("tenantId", tenantId.bytes())
                .param("customerId", Uuids.toBytes(customerId))
                .param("channel", channel.dbValue())
                .query(MAPPER)
                .optional();
    }

    boolean anyForCustomer(TenantId tenantId, UUID customerId) {
        return jdbc.sql("SELECT COUNT(*) FROM conversations WHERE tenant_id = :tenantId AND customer_id = :customerId")
                .param("tenantId", tenantId.bytes())
                .param("customerId", Uuids.toBytes(customerId))
                .query(Integer.class)
                .single() > 0;
    }

    void insert(TenantId tenantId, UUID id, UUID customerId, Channel channel, Instant now) {
        jdbc.sql("""
                INSERT INTO conversations (id, tenant_id, customer_id, channel, status, opened_at, last_message_at,
                                           created_at, updated_at)
                VALUES (:id, :tenantId, :customerId, :channel, 'bot', :now, :now, :now, :now)
                """)
                .param("id", Uuids.toBytes(id))
                .param("tenantId", tenantId.bytes())
                .param("customerId", Uuids.toBytes(customerId))
                .param("channel", channel.dbValue())
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    void recordCustomerMessage(TenantId tenantId, UUID id, Instant at) {
        jdbc.sql("""
                UPDATE conversations
                   SET last_customer_message_at = :at, window_expires_at = :windowExpires, last_message_at = :at,
                       unread_count = unread_count + 1, updated_at = :at
                 WHERE tenant_id = :tenantId AND id = :id
                """)
                .param("tenantId", tenantId.bytes())
                .param("id", Uuids.toBytes(id))
                .param("at", ZamuTime.toDb(at))
                .param("windowExpires", ZamuTime.toDb(at.plus(Conversation.WINDOW)))
                .update();
    }

    void touch(TenantId tenantId, UUID id, Instant at) {
        jdbc.sql("UPDATE conversations SET last_message_at = :at, updated_at = :at WHERE tenant_id = :tenantId AND id = :id")
                .param("tenantId", tenantId.bytes())
                .param("id", Uuids.toBytes(id))
                .param("at", ZamuTime.toDb(at))
                .update();
    }

    void startBillablePeriod(TenantId tenantId, UUID id, Instant at) {
        jdbc.sql("UPDATE conversations SET billable_session_start = :at WHERE tenant_id = :tenantId AND id = :id")
                .param("tenantId", tenantId.bytes())
                .param("id", Uuids.toBytes(id))
                .param("at", ZamuTime.toDb(at))
                .update();
    }

    void markHoldingReplySent(TenantId tenantId, UUID id) {
        jdbc.sql("UPDATE conversations SET holding_reply_sent = TRUE WHERE tenant_id = :tenantId AND id = :id")
                .param("tenantId", tenantId.bytes())
                .param("id", Uuids.toBytes(id))
                .update();
    }

    int updateStatus(TenantId tenantId, UUID id, ConversationStatus status, UUID assignedUserId, Instant now) {
        return jdbc.sql("""
                UPDATE conversations
                   SET status = :status, assigned_user_id = :assigned,
                       closed_at = CASE WHEN :status = 'closed' THEN :now ELSE NULL END, updated_at = :now
                 WHERE tenant_id = :tenantId AND id = :id
                """)
                .param("tenantId", tenantId.bytes())
                .param("id", Uuids.toBytes(id))
                .param("status", status.dbValue())
                .param("assigned", Uuids.toBytes(assignedUserId))
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    void resetUnread(TenantId tenantId, UUID id) {
        jdbc.sql("UPDATE conversations SET unread_count = 0 WHERE tenant_id = :tenantId AND id = :id")
                .param("tenantId", tenantId.bytes())
                .param("id", Uuids.toBytes(id))
                .update();
    }

    List<InboxRow> inbox(TenantId tenantId, InboxFilter filter, String search, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT c.id, c.channel, c.status, c.assigned_user_id, c.last_message_at, c.unread_count,
                       cu.id AS customer_id, cu.name, cu.phone
                  FROM conversations c
                  JOIN customers cu ON cu.id = c.customer_id AND cu.tenant_id = c.tenant_id
                 WHERE c.tenant_id = :tenantId
                """);
        switch (filter) {
            case OPEN -> sql.append(" AND c.status <> 'closed'");
            case ATTENTION -> sql.append(" AND c.status = 'handed_over' AND c.assigned_user_id IS NULL");
            case HANDED_OVER -> sql.append(" AND c.status = 'handed_over'");
            case BOT -> sql.append(" AND c.status = 'bot'");
            case CLOSED -> sql.append(" AND c.status = 'closed'");
        }
        String term = search == null ? "" : search.trim();
        String digits = term.replaceAll("\\D", "");
        if (!term.isEmpty()) {
            sql.append(digits.length() >= 3 ? " AND (cu.name LIKE :name OR cu.phone LIKE :phone)" : " AND cu.name LIKE :name");
        }
        sql.append(" ORDER BY c.last_message_at DESC, c.id DESC LIMIT :limit");

        JdbcClient.StatementSpec spec = jdbc.sql(sql.toString())
                .param("tenantId", tenantId.bytes())
                .param("limit", limit);
        if (!term.isEmpty()) {
            spec = spec.param("name", "%" + escapeLike(term) + "%");
            if (digits.length() >= 3) {
                spec = spec.param("phone", "%" + digits + "%");
            }
        }
        return spec.query((rs, rowNum) -> new InboxRow(
                        Uuids.read(rs, "id"),
                        Uuids.read(rs, "customer_id"),
                        rs.getString("name"),
                        rs.getString("phone"),
                        Channel.fromDb(rs.getString("channel")),
                        ConversationStatus.fromDb(rs.getString("status")),
                        Uuids.read(rs, "assigned_user_id"),
                        ZamuTime.read(rs, "last_message_at"),
                        rs.getInt("unread_count")))
                .list();
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
