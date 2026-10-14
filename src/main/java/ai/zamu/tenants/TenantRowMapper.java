package ai.zamu.tenants;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.jdbc.core.RowMapper;

import ai.zamu.shared.ids.Uuids;
import ai.zamu.shared.time.ZamuTime;

class TenantRowMapper implements RowMapper<Tenant> {

    static final String COLUMNS = """
            id, name, display_name, plan, status, timezone, default_language, tone, conversation_limit,
            confidence_threshold, created_at, updated_at""";

    static final TenantRowMapper INSTANCE = new TenantRowMapper();

    @Override
    public Tenant mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Tenant(
                Uuids.read(rs, "id"),
                rs.getString("name"),
                rs.getString("display_name"),
                TenantPlan.fromDb(rs.getString("plan")),
                TenantStatus.fromDb(rs.getString("status")),
                rs.getString("timezone"),
                TenantLanguage.fromDb(rs.getString("default_language")),
                TenantTone.fromDb(rs.getString("tone")),
                rs.getInt("conversation_limit"),
                rs.getBigDecimal("confidence_threshold"),
                ZamuTime.read(rs, "created_at"),
                ZamuTime.read(rs, "updated_at"));
    }
}
