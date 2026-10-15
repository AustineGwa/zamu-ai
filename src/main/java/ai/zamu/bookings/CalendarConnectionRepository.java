package ai.zamu.bookings;

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

/** The calendar_connections table (BK-01). The refresh token is stored encrypted (NFR-06). */
@Component
class CalendarConnectionRepository {

    record Row(UUID id, UUID tenantId, String calendarId, byte[] refreshToken, String status, String lastError,
            Instant updatedAt) {

        boolean connected() {
            return "connected".equals(status);
        }
    }

    private final JdbcClient jdbc;

    CalendarConnectionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<Row> find(TenantId tenantId) {
        return jdbc.sql("""
                SELECT id, tenant_id, calendar_id, oauth_refresh_token, status, last_error, updated_at
                  FROM calendar_connections WHERE tenant_id = :tenantId
                """)
                .param("tenantId", tenantId.bytes())
                .query((rs, n) -> new Row(Uuids.read(rs, "id"), Uuids.read(rs, "tenant_id"), rs.getString("calendar_id"),
                        rs.getBytes("oauth_refresh_token"), rs.getString("status"), rs.getString("last_error"),
                        ZamuTime.read(rs, "updated_at")))
                .optional();
    }

    @CrossTenant("Calendar sync sweep (BK-10): lists which tenants have a connected calendar")
    List<UUID> findConnectedTenantIds() {
        return jdbc.sql("SELECT tenant_id FROM calendar_connections WHERE status = 'connected'")
                .query((rs, n) -> Uuids.read(rs, "tenant_id"))
                .list();
    }

    void save(TenantId tenantId, UUID id, String calendarId, byte[] encryptedToken, Instant now) {
        int updated = jdbc.sql("""
                UPDATE calendar_connections SET calendar_id = :calendarId, oauth_refresh_token = :token,
                       status = 'connected', last_error = NULL, updated_at = :now
                 WHERE tenant_id = :tenantId
                """)
                .param("tenantId", tenantId.bytes())
                .param("calendarId", calendarId)
                .param("token", encryptedToken)
                .param("now", ZamuTime.toDb(now))
                .update();
        if (updated == 0) {
            jdbc.sql("""
                    INSERT INTO calendar_connections (id, tenant_id, provider, calendar_id, oauth_refresh_token, status,
                                                      created_at, updated_at)
                    VALUES (:id, :tenantId, 'google', :calendarId, :token, 'connected', :now, :now)
                    """)
                    .param("id", Uuids.toBytes(id))
                    .param("tenantId", tenantId.bytes())
                    .param("calendarId", calendarId)
                    .param("token", encryptedToken)
                    .param("now", ZamuTime.toDb(now))
                    .update();
        }
    }

    void setCalendarId(TenantId tenantId, String calendarId, Instant now) {
        jdbc.sql("UPDATE calendar_connections SET calendar_id = :calendarId, updated_at = :now WHERE tenant_id = :tenantId")
                .param("tenantId", tenantId.bytes())
                .param("calendarId", calendarId)
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    /** @return 1 when the status changed, 0 when it was already revoked (alert only once) */
    int markRevoked(TenantId tenantId, String error, Instant now) {
        return jdbc.sql("""
                UPDATE calendar_connections SET status = 'revoked', last_error = :error, updated_at = :now
                 WHERE tenant_id = :tenantId AND status <> 'revoked'
                """)
                .param("tenantId", tenantId.bytes())
                .param("error", error)
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    void delete(TenantId tenantId) {
        jdbc.sql("DELETE FROM calendar_connections WHERE tenant_id = :tenantId")
                .param("tenantId", tenantId.bytes())
                .update();
    }
}
