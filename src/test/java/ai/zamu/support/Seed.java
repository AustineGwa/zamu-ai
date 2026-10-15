package ai.zamu.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.ids.Uuids;
import ai.zamu.shared.time.ZamuTime;

/**
 * Direct inserts for report, privacy and monitoring tests, where exact timestamps matter more than going through
 * the conversation engine. Test code only.
 */
public final class Seed {

    private Seed() {
    }

    public static UUID customer(JdbcClient jdbc, TenantId t, String phone, String name, Instant at) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO customers (id, tenant_id, phone, name, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)
                """).params(Uuids.toBytes(id), t.bytes(), phone, name, ZamuTime.toDb(at), ZamuTime.toDb(at)).update();
        return id;
    }

    public static UUID conversation(JdbcClient jdbc, TenantId t, UUID customerId, Instant openedAt, Instant lastAt) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO conversations (id, tenant_id, customer_id, channel, status, opened_at, last_message_at,
                                           last_customer_message_at, created_at, updated_at)
                VALUES (?, ?, ?, 'whatsapp', 'bot', ?, ?, ?, ?, ?)
                """).params(Uuids.toBytes(id), t.bytes(), Uuids.toBytes(customerId), ZamuTime.toDb(openedAt),
                ZamuTime.toDb(lastAt), ZamuTime.toDb(lastAt), ZamuTime.toDb(openedAt), ZamuTime.toDb(lastAt)).update();
        return id;
    }

    /** direction in/out; sender customer/bot/staff; kbEntryIds is a JSON array or null. */
    public static UUID message(JdbcClient jdbc, TenantId t, UUID conversationId, String direction, String sender,
            String body, String status, String kbEntryIds, Instant at) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO messages (id, tenant_id, conversation_id, direction, sender, type, body, status, kb_entry_ids,
                                      created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'text', ?, ?, ?, ?, ?)
                """).params(Uuids.toBytes(id), t.bytes(), Uuids.toBytes(conversationId), direction, sender, body, status,
                kbEntryIds, ZamuTime.toDb(at), ZamuTime.toDb(at)).update();
        return id;
    }

    public static UUID handover(JdbcClient jdbc, TenantId t, UUID conversationId, String reason, Instant requestedAt,
            Instant firstStaffReplyAt) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO handovers (id, tenant_id, conversation_id, reason, requested_at, first_staff_reply_at,
                                       created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """).params(Uuids.toBytes(id), t.bytes(), Uuids.toBytes(conversationId), reason,
                ZamuTime.toDb(requestedAt), firstStaffReplyAt == null ? null : ZamuTime.toDb(firstStaffReplyAt),
                ZamuTime.toDb(requestedAt), ZamuTime.toDb(requestedAt)).update();
        return id;
    }

    public static UUID kbEntry(JdbcClient jdbc, TenantId t, String title, Instant at) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO kb_entries (id, tenant_id, title, question, answer_en, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'Answer', 'approved', ?, ?)
                """).params(Uuids.toBytes(id), t.bytes(), title, title + "?", ZamuTime.toDb(at), ZamuTime.toDb(at)).update();
        return id;
    }

    public static void usage(JdbcClient jdbc, TenantId t, LocalDate day, int conversations, BigDecimal aiCostUsd,
            int waBillable) {
        jdbc.sql("""
                INSERT INTO usage_daily (tenant_id, usage_date, conversations, messages_in, messages_out, ai_cost_usd,
                                         wa_billable_messages)
                VALUES (?, ?, ?, 0, 0, ?, ?)
                """).params(t.bytes(), day, conversations, aiCostUsd, waBillable).update();
    }

    public static UUID platformUser(JdbcClient jdbc, String email, String role) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO users (id, tenant_id, email, name, role, created_at, updated_at)
                VALUES (?, NULL, ?, 'Zamu team', ?, NOW(3), NOW(3))
                """).params(Uuids.toBytes(id), email, role).update();
        return id;
    }

    /** status queued, running, done, failed or dead. */
    public static UUID job(JdbcClient jdbc, String type, String status, int attempts, Instant runAt, Instant updatedAt) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO jobs (id, type, payload, run_at, status, attempts, max_attempts, last_error, created_at, updated_at)
                VALUES (?, ?, '{}', ?, ?, ?, 5, ?, ?, ?)
                """).params(Uuids.toBytes(id), type, ZamuTime.toDb(runAt), status, attempts,
                "dead".equals(status) ? "boom" : null, ZamuTime.toDb(runAt), ZamuTime.toDb(updatedAt)).update();
        return id;
    }
}
