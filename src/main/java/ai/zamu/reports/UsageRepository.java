package ai.zamu.reports;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import ai.zamu.shared.ids.TenantId;

/** The usage_daily table (spec: usage_daily; MSG-17, AI-11). */
@Component
class UsageRepository {

    private final JdbcClient jdbc;

    UsageRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void increment(TenantId tenantId, LocalDate date, String column) {
        if (!column.matches("conversations|messages_in|messages_out")) {
            throw new IllegalArgumentException("Unknown usage column");
        }
        jdbc.sql("INSERT INTO usage_daily (tenant_id, usage_date, " + column + ") VALUES (:tenantId, :date, 1) "
                        + "ON DUPLICATE KEY UPDATE " + column + " = " + column + " + 1")
                .param("tenantId", tenantId.bytes())
                .param("date", date)
                .update();
    }

    Optional<DailyUsage> find(TenantId tenantId, LocalDate date) {
        return jdbc.sql("""
                SELECT usage_date, conversations, messages_in, messages_out
                  FROM usage_daily
                 WHERE tenant_id = :tenantId AND usage_date = :date
                """)
                .param("tenantId", tenantId.bytes())
                .param("date", date)
                .query((rs, rowNum) -> new DailyUsage(rs.getObject("usage_date", LocalDate.class),
                        rs.getInt("conversations"), rs.getInt("messages_in"), rs.getInt("messages_out")))
                .optional();
    }
}
