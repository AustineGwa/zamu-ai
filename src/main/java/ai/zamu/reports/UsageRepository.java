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
        if (!column.matches("conversations|messages_in|messages_out|wa_billable_messages")) {
            throw new IllegalArgumentException("Unknown usage column");
        }
        jdbc.sql("INSERT INTO usage_daily (tenant_id, usage_date, " + column + ") VALUES (:tenantId, :date, 1) "
                        + "ON DUPLICATE KEY UPDATE " + column + " = " + column + " + 1")
                .param("tenantId", tenantId.bytes())
                .param("date", date)
                .update();
    }

    void addAi(TenantId tenantId, LocalDate date, long tokensIn, long tokensOut, java.math.BigDecimal costUsd) {
        jdbc.sql("""
                INSERT INTO usage_daily (tenant_id, usage_date, ai_tokens_in, ai_tokens_out, ai_cost_usd)
                VALUES (:tenantId, :date, :in, :out, :cost)
                ON DUPLICATE KEY UPDATE ai_tokens_in = ai_tokens_in + :in, ai_tokens_out = ai_tokens_out + :out,
                                        ai_cost_usd = ai_cost_usd + :cost
                """)
                .param("tenantId", tenantId.bytes())
                .param("date", date)
                .param("in", tokensIn)
                .param("out", tokensOut)
                .param("cost", costUsd)
                .update();
    }

    Optional<DailyUsage> find(TenantId tenantId, LocalDate date) {
        return jdbc.sql("""
                SELECT usage_date, conversations, messages_in, messages_out, ai_tokens_in, ai_tokens_out, ai_cost_usd
                  FROM usage_daily
                 WHERE tenant_id = :tenantId AND usage_date = :date
                """)
                .param("tenantId", tenantId.bytes())
                .param("date", date)
                .query((rs, rowNum) -> new DailyUsage(rs.getObject("usage_date", LocalDate.class),
                        rs.getInt("conversations"), rs.getInt("messages_in"), rs.getInt("messages_out"),
                        rs.getLong("ai_tokens_in"), rs.getLong("ai_tokens_out"), rs.getBigDecimal("ai_cost_usd")))
                .optional();
    }
}
