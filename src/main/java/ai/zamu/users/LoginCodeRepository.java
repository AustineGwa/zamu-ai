package ai.zamu.users;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import ai.zamu.shared.ids.Uuids;
import ai.zamu.shared.security.CrossTenant;
import ai.zamu.shared.time.ZamuTime;

/** login_codes is not tenant-owned: codes are requested before anyone knows which tenant the user is in. */
@Component
class LoginCodeRepository {

    private static final RowMapper<LoginCode> ROW_MAPPER = (rs, rowNum) -> new LoginCode(
            Uuids.read(rs, "id"),
            rs.getString("email"),
            rs.getBytes("code_hash"),
            ZamuTime.read(rs, "expires_at"),
            rs.getInt("attempts"),
            ZamuTime.read(rs, "consumed_at"),
            ZamuTime.read(rs, "created_at"));

    private final JdbcClient jdbc;

    LoginCodeRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @CrossTenant("Login codes are keyed by email, before the tenant is known")
    void insert(LoginCode code) {
        jdbc.sql("""
                INSERT INTO login_codes (id, email, code_hash, expires_at, attempts, consumed_at, created_at)
                VALUES (:id, :email, :codeHash, :expiresAt, 0, NULL, :createdAt)
                """)
                .param("id", Uuids.toBytes(code.id()))
                .param("email", code.email())
                .param("codeHash", code.codeHash())
                .param("expiresAt", ZamuTime.toDb(code.expiresAt()))
                .param("createdAt", ZamuTime.toDb(code.createdAt()))
                .update();
    }

    @CrossTenant("Login codes are keyed by email, before the tenant is known")
    int countCreatedSince(String email, Instant since) {
        return jdbc.sql("SELECT COUNT(*) FROM login_codes WHERE email = :email AND created_at >= :since")
                .param("email", email)
                .param("since", ZamuTime.toDb(since))
                .query(Integer.class)
                .single();
    }

    /** The newest unused, unexpired code for the email, locked for the check (must run in a transaction). */
    @CrossTenant("Login codes are keyed by email, before the tenant is known")
    Optional<LoginCode> findLatestUsableForUpdate(String email, Instant now) {
        return jdbc.sql("""
                SELECT id, email, code_hash, expires_at, attempts, consumed_at, created_at
                  FROM login_codes
                 WHERE email = :email AND consumed_at IS NULL AND expires_at > :now
                 ORDER BY created_at DESC, id DESC
                 LIMIT 1
                 FOR UPDATE
                """)
                .param("email", email)
                .param("now", ZamuTime.toDb(now))
                .query(ROW_MAPPER)
                .optional();
    }

    @CrossTenant("Updates one code by its own ID")
    void incrementAttempts(UUID id) {
        jdbc.sql("UPDATE login_codes SET attempts = attempts + 1 WHERE id = :id")
                .param("id", Uuids.toBytes(id))
                .update();
    }

    @CrossTenant("Updates one code by its own ID")
    void markConsumed(UUID id, Instant now) {
        jdbc.sql("UPDATE login_codes SET consumed_at = :now WHERE id = :id")
                .param("id", Uuids.toBytes(id))
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    @CrossTenant("Housekeeping across all codes; no tenant data")
    int deleteCreatedBefore(Instant cutoff) {
        return jdbc.sql("DELETE FROM login_codes WHERE created_at < :cutoff")
                .param("cutoff", ZamuTime.toDb(cutoff))
                .update();
    }
}
