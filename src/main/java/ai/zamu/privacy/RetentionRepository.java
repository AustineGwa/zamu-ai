package ai.zamu.privacy;

import java.time.Instant;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.time.ZamuTime;

/** The retention_settings table. */
@Component
class RetentionRepository {

    private final JdbcClient jdbc;

    RetentionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<RetentionSettings> find(TenantId tenantId) {
        return jdbc.sql("SELECT conversation_months, offboard_purge_days FROM retention_settings WHERE tenant_id = :tenantId")
                .param("tenantId", tenantId.bytes())
                .query((rs, n) -> new RetentionSettings(rs.getInt("conversation_months"), rs.getInt("offboard_purge_days")))
                .optional();
    }

    void save(TenantId tenantId, RetentionSettings s, Instant now) {
        int updated = jdbc.sql("""
                UPDATE retention_settings SET conversation_months = :months, offboard_purge_days = :days, updated_at = :now
                 WHERE tenant_id = :tenantId
                """)
                .param("tenantId", tenantId.bytes()).param("months", s.conversationMonths())
                .param("days", s.offboardPurgeDays()).param("now", ZamuTime.toDb(now))
                .update();
        if (updated == 0) {
            jdbc.sql("""
                    INSERT INTO retention_settings (tenant_id, conversation_months, offboard_purge_days, updated_at)
                    VALUES (:tenantId, :months, :days, :now)
                    """)
                    .param("tenantId", tenantId.bytes()).param("months", s.conversationMonths())
                    .param("days", s.offboardPurgeDays()).param("now", ZamuTime.toDb(now))
                    .update();
        }
    }
}
