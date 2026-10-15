package ai.zamu.users;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.ids.Uuids;
import ai.zamu.shared.security.CrossTenant;
import ai.zamu.shared.security.ZamuRole;
import ai.zamu.shared.time.ZamuTime;

/**
 * The users table. Methods on a tenant's users take its {@link TenantId} and filter by it (NFR-09). Login
 * and Zamu team user methods are marked {@link CrossTenant} with the reason.
 */
@Component
class UserRepository {

    private final JdbcClient jdbc;

    UserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ---- Tenant-scoped -------------------------------------------------------------------------------

    void insertTenantUser(TenantId tenantId, User user) {
        if (user.tenantId() == null || !user.tenantId().equals(tenantId.value()) || user.role().isPlatformStaff()) {
            throw new IllegalArgumentException("Tenant user must belong to the given tenant");
        }
        insert(user);
    }

    List<User> findAllInTenant(TenantId tenantId) {
        return jdbc.sql("SELECT " + UserRowMapper.COLUMNS + """
                 FROM users
                WHERE tenant_id = :tenantId
                ORDER BY disabled_at IS NOT NULL, name, id
                """)
                .param("tenantId", tenantId.bytes())
                .query(UserRowMapper.INSTANCE)
                .list();
    }

    Optional<User> findInTenant(TenantId tenantId, UUID userId) {
        return jdbc.sql("SELECT " + UserRowMapper.COLUMNS + " FROM users WHERE tenant_id = :tenantId AND id = :id")
                .param("tenantId", tenantId.bytes())
                .param("id", Uuids.toBytes(userId))
                .query(UserRowMapper.INSTANCE)
                .optional();
    }

    int countActiveInTenant(TenantId tenantId) {
        return jdbc.sql("SELECT COUNT(*) FROM users WHERE tenant_id = :tenantId AND disabled_at IS NULL")
                .param("tenantId", tenantId.bytes())
                .query(Integer.class)
                .single();
    }

    int disableInTenant(TenantId tenantId, UUID userId, Instant now) {
        return jdbc.sql("""
                UPDATE users SET disabled_at = :now, updated_at = :now
                 WHERE tenant_id = :tenantId AND id = :id AND disabled_at IS NULL
                """)
                .param("tenantId", tenantId.bytes())
                .param("id", Uuids.toBytes(userId))
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    // ---- Login and Zamu team users ---------------------------------------------------------------------

    @CrossTenant("Login: the user is identified by email before any tenant is known")
    Optional<User> findActiveByEmail(String email) {
        return jdbc.sql("SELECT " + UserRowMapper.COLUMNS + " FROM users WHERE email = :email AND disabled_at IS NULL")
                .param("email", email)
                .query(UserRowMapper.INSTANCE)
                .optional();
    }

    @CrossTenant("Zamu team members have no tenant; used to alert operators about integration problems")
    List<String> findActivePlatformEmails() {
        return jdbc.sql("""
                SELECT email FROM users WHERE tenant_id IS NULL AND disabled_at IS NULL
                   AND role IN ('operator', 'admin') ORDER BY email
                """)
                .query(String.class)
                .list();
    }

    @CrossTenant("Emails are unique across all tenants because login is by email alone")
    boolean existsByEmail(String email) {
        return jdbc.sql("SELECT COUNT(*) FROM users WHERE email = :email")
                .param("email", email)
                .query(Integer.class)
                .single() > 0;
    }

    @CrossTenant("Login completion: the ID comes from the server-side session after the email code was verified")
    Optional<User> findActiveById(UUID userId) {
        return jdbc.sql("SELECT " + UserRowMapper.COLUMNS + " FROM users WHERE id = :id AND disabled_at IS NULL")
                .param("id", Uuids.toBytes(userId))
                .query(UserRowMapper.INSTANCE)
                .optional();
    }

    @CrossTenant("Login: records the login time on the user's own row")
    void recordLogin(UUID userId, Instant now) {
        jdbc.sql("UPDATE users SET last_login_at = :now, updated_at = :now WHERE id = :id")
                .param("id", Uuids.toBytes(userId))
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    @CrossTenant("Second factor for Zamu team users, who have no tenant")
    int enableTotp(UUID userId, byte[] encryptedSecret, long step, Instant now) {
        return jdbc.sql("""
                UPDATE users SET totp_secret = :secret, totp_enabled_at = :now, totp_last_step = :step, updated_at = :now
                 WHERE id = :id AND tenant_id IS NULL AND totp_enabled_at IS NULL
                """)
                .param("id", Uuids.toBytes(userId))
                .param("secret", encryptedSecret)
                .param("step", step)
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    /** Compare-and-set, so two requests can't both use the same code (replay protection). */
    @CrossTenant("Second factor for Zamu team users, who have no tenant")
    boolean advanceTotpStep(UUID userId, long step) {
        return jdbc.sql("""
                UPDATE users SET totp_last_step = :step
                 WHERE id = :id AND tenant_id IS NULL AND (totp_last_step IS NULL OR totp_last_step < :step)
                """)
                .param("id", Uuids.toBytes(userId))
                .param("step", step)
                .update() == 1;
    }

    @CrossTenant("Zamu team users have no tenant")
    void insertPlatformUser(User user) {
        if (user.tenantId() != null || !user.role().isPlatformStaff()) {
            throw new IllegalArgumentException("Zamu team users have no tenant");
        }
        insert(user);
    }

    @CrossTenant("Startup check for the first Zamu admin")
    boolean anyActiveWithRole(ZamuRole role) {
        return jdbc.sql("SELECT COUNT(*) FROM users WHERE role = :role AND disabled_at IS NULL")
                .param("role", role.dbValue())
                .query(Integer.class)
                .single() > 0;
    }

    private void insert(User user) {
        jdbc.sql("""
                INSERT INTO users (id, tenant_id, email, name, role, created_at, updated_at)
                VALUES (:id, :tenantId, :email, :name, :role, :createdAt, :updatedAt)
                """)
                .param("id", Uuids.toBytes(user.id()))
                .param("tenantId", Uuids.toBytes(user.tenantId()))
                .param("email", user.email())
                .param("name", user.name())
                .param("role", user.role().dbValue())
                .param("createdAt", ZamuTime.toDb(user.createdAt()))
                .param("updatedAt", ZamuTime.toDb(user.updatedAt()))
                .update();
    }
}
