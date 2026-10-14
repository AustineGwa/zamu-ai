package ai.zamu.leads;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.jdbc.core.RowMapper;

import ai.zamu.shared.ids.Uuids;
import ai.zamu.shared.time.ZamuTime;

class LeadRowMapper implements RowMapper<Lead> {

    static final String COLUMNS = """
            id, business_name, contact_name, phone, email, business_type, message, status, consent_at,
            created_at, updated_at""";

    static final LeadRowMapper INSTANCE = new LeadRowMapper();

    @Override
    public Lead mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Lead(
                Uuids.read(rs, "id"),
                rs.getString("business_name"),
                rs.getString("contact_name"),
                rs.getString("phone"),
                rs.getString("email"),
                BusinessType.fromDb(rs.getString("business_type")),
                rs.getString("message"),
                LeadStatus.fromDb(rs.getString("status")),
                ZamuTime.read(rs, "consent_at"),
                ZamuTime.read(rs, "created_at"),
                ZamuTime.read(rs, "updated_at"));
    }
}
