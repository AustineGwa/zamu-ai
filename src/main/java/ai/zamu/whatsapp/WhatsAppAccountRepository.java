package ai.zamu.whatsapp;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.ids.Uuids;
import ai.zamu.shared.security.CrossTenant;
import ai.zamu.shared.time.ZamuTime;

/** The whatsapp_accounts table: one row per tenant in version 1. */
@Component
class WhatsAppAccountRepository {

    private static final String COLUMNS = """
            id, tenant_id, waba_id, phone_number_id, display_phone, access_token, webhook_verified, quality_rating,
            created_at, updated_at""";

    private static final RowMapper<WhatsAppAccount> MAPPER = (rs, rowNum) -> new WhatsAppAccount(
            Uuids.read(rs, "id"),
            Uuids.read(rs, "tenant_id"),
            rs.getString("waba_id"),
            rs.getString("phone_number_id"),
            rs.getString("display_phone"),
            rs.getBytes("access_token"),
            rs.getBoolean("webhook_verified"),
            rs.getString("quality_rating"),
            ZamuTime.read(rs, "created_at"),
            ZamuTime.read(rs, "updated_at"));

    private final JdbcClient jdbc;

    WhatsAppAccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** MSG-03: routing an inbound webhook to its tenant by the receiving number's ID. */
    @CrossTenant("Webhook routing: the tenant is what this lookup finds out (MSG-03)")
    Optional<WhatsAppAccount> findByPhoneNumberId(String phoneNumberId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM whatsapp_accounts WHERE phone_number_id = :phoneNumberId")
                .param("phoneNumberId", phoneNumberId)
                .query(MAPPER)
                .optional();
    }

    @CrossTenant("Template status webhooks identify the account by WABA ID; the tenant is what this lookup finds out")
    Optional<WhatsAppAccount> findByWabaId(String wabaId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM whatsapp_accounts WHERE waba_id = :wabaId LIMIT 1")
                .param("wabaId", wabaId)
                .query(MAPPER)
                .optional();
    }

    Optional<WhatsAppAccount> findByTenant(TenantId tenantId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM whatsapp_accounts WHERE tenant_id = :tenantId")
                .param("tenantId", tenantId.bytes())
                .query(MAPPER)
                .optional();
    }

    void insert(TenantId tenantId, UUID id, String wabaId, String phoneNumberId, String displayPhone,
            byte[] encryptedToken, Instant now) {
        jdbc.sql("""
                INSERT INTO whatsapp_accounts (id, tenant_id, waba_id, phone_number_id, display_phone, access_token,
                                               created_at, updated_at)
                VALUES (:id, :tenantId, :wabaId, :phoneNumberId, :displayPhone, :token, :now, :now)
                """)
                .param("id", Uuids.toBytes(id))
                .param("tenantId", tenantId.bytes())
                .param("wabaId", wabaId)
                .param("phoneNumberId", phoneNumberId)
                .param("displayPhone", displayPhone)
                .param("token", encryptedToken)
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    void update(TenantId tenantId, String wabaId, String phoneNumberId, String displayPhone, byte[] encryptedTokenOrNull,
            Instant now) {
        jdbc.sql("""
                UPDATE whatsapp_accounts
                   SET waba_id = :wabaId, phone_number_id = :phoneNumberId, display_phone = :displayPhone,
                       access_token = COALESCE(:token, access_token), updated_at = :now
                 WHERE tenant_id = :tenantId
                """)
                .param("tenantId", tenantId.bytes())
                .param("wabaId", wabaId)
                .param("phoneNumberId", phoneNumberId)
                .param("displayPhone", displayPhone)
                .param("token", encryptedTokenOrNull)
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    void markWebhookVerified(TenantId tenantId) {
        jdbc.sql("UPDATE whatsapp_accounts SET webhook_verified = TRUE WHERE tenant_id = :tenantId AND webhook_verified = FALSE")
                .param("tenantId", tenantId.bytes())
                .update();
    }

    @CrossTenant("Uniqueness check: a phone number ID can belong to only one tenant")
    boolean phoneNumberIdTakenByOther(String phoneNumberId, UUID tenantId) {
        return jdbc.sql("SELECT COUNT(*) FROM whatsapp_accounts WHERE phone_number_id = :phoneNumberId AND tenant_id <> :tenantId")
                .param("phoneNumberId", phoneNumberId)
                .param("tenantId", Uuids.toBytes(tenantId))
                .query(Integer.class)
                .single() > 0;
    }

    int deleteForTenant(TenantId tenantId) {
        return jdbc.sql("DELETE FROM whatsapp_accounts WHERE tenant_id = :tenantId").param("tenantId", tenantId.bytes()).update();
    }
}
