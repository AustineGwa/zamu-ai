package ai.zamu.users;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.jdbc.core.RowMapper;

import ai.zamu.shared.ids.Uuids;
import ai.zamu.shared.security.ZamuRole;
import ai.zamu.shared.time.ZamuTime;

class UserRowMapper implements RowMapper<User> {

    static final String COLUMNS = """
            id, tenant_id, email, name, role, last_login_at, disabled_at, totp_secret, totp_enabled_at,
            totp_last_step, created_at, updated_at""";

    static final UserRowMapper INSTANCE = new UserRowMapper();

    @Override
    public User mapRow(ResultSet rs, int rowNum) throws SQLException {
        long step = rs.getLong("totp_last_step");
        Long lastStep = rs.wasNull() ? null : step;
        return new User(
                Uuids.read(rs, "id"),
                Uuids.read(rs, "tenant_id"),
                rs.getString("email"),
                rs.getString("name"),
                ZamuRole.fromDb(rs.getString("role")),
                ZamuTime.read(rs, "last_login_at"),
                ZamuTime.read(rs, "disabled_at"),
                rs.getBytes("totp_secret"),
                ZamuTime.read(rs, "totp_enabled_at"),
                lastStep,
                ZamuTime.read(rs, "created_at"),
                ZamuTime.read(rs, "updated_at"));
    }
}
