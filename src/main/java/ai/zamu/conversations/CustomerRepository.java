package ai.zamu.conversations;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.ids.Uuids;
import ai.zamu.shared.time.ZamuTime;

/** The customers table. Every method is scoped to one tenant (NFR-09). */
@Component
class CustomerRepository {

    private static final String COLUMNS = """
            id, tenant_id, phone, name, language, opted_in_marketing, opted_out_at, blocked, deleted_at,
            created_at, updated_at""";

    private static final RowMapper<Customer> MAPPER = (rs, rowNum) -> new Customer(
            Uuids.read(rs, "id"),
            Uuids.read(rs, "tenant_id"),
            rs.getString("phone"),
            rs.getString("name"),
            rs.getString("language"),
            rs.getBoolean("opted_in_marketing"),
            ZamuTime.read(rs, "opted_out_at"),
            rs.getBoolean("blocked"),
            ZamuTime.read(rs, "deleted_at"),
            ZamuTime.read(rs, "created_at"),
            ZamuTime.read(rs, "updated_at"));

    private final JdbcClient jdbc;

    CustomerRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<Customer> findByPhone(TenantId tenantId, String phone) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM customers WHERE tenant_id = :tenantId AND phone = :phone")
                .param("tenantId", tenantId.bytes())
                .param("phone", phone)
                .query(MAPPER)
                .optional();
    }

    Optional<Customer> findById(TenantId tenantId, UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM customers WHERE tenant_id = :tenantId AND id = :id")
                .param("tenantId", tenantId.bytes())
                .param("id", Uuids.toBytes(id))
                .query(MAPPER)
                .optional();
    }

    void insert(TenantId tenantId, UUID id, String phone, String name, Instant now) {
        jdbc.sql("""
                INSERT INTO customers (id, tenant_id, phone, name, created_at, updated_at)
                VALUES (:id, :tenantId, :phone, :name, :now, :now)
                """)
                .param("id", Uuids.toBytes(id))
                .param("tenantId", tenantId.bytes())
                .param("phone", phone)
                .param("name", name)
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    void updateNameIfMissing(TenantId tenantId, UUID id, String name, Instant now) {
        jdbc.sql("UPDATE customers SET name = :name, updated_at = :now WHERE tenant_id = :tenantId AND id = :id AND name IS NULL")
                .param("tenantId", tenantId.bytes())
                .param("id", Uuids.toBytes(id))
                .param("name", name)
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    void updateLanguage(TenantId tenantId, UUID id, String language, Instant now) {
        jdbc.sql("UPDATE customers SET language = :language, updated_at = :now WHERE tenant_id = :tenantId AND id = :id")
                .param("tenantId", tenantId.bytes())
                .param("id", Uuids.toBytes(id))
                .param("language", language)
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    void optOut(TenantId tenantId, UUID id, Instant now) {
        jdbc.sql("""
                UPDATE customers SET opted_out_at = :now, opted_in_marketing = FALSE, updated_at = :now
                 WHERE tenant_id = :tenantId AND id = :id
                """)
                .param("tenantId", tenantId.bytes())
                .param("id", Uuids.toBytes(id))
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    int setBlocked(TenantId tenantId, UUID id, boolean blocked, Instant now) {
        return jdbc.sql("UPDATE customers SET blocked = :blocked, updated_at = :now WHERE tenant_id = :tenantId AND id = :id")
                .param("tenantId", tenantId.bytes())
                .param("id", Uuids.toBytes(id))
                .param("blocked", blocked)
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    // --- privacy (NFR-11, NFR-12) ---

    int delete(TenantId tenantId, UUID id) {
        return jdbc.sql("DELETE FROM customers WHERE tenant_id = :tenantId AND id = :id")
                .param("tenantId", tenantId.bytes())
                .param("id", Uuids.toBytes(id))
                .update();
    }

    int deleteAllForTenant(TenantId tenantId) {
        return jdbc.sql("DELETE FROM customers WHERE tenant_id = :tenantId").param("tenantId", tenantId.bytes()).update();
    }
}
