package ai.zamu.leads;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import ai.zamu.shared.ids.Uuids;
import ai.zamu.shared.security.CrossTenant;
import ai.zamu.shared.time.ZamuTime;

/** The leads table. Leads are prospects of the Zamu team, not tenant data, so no method takes a tenant. */
@Component
class LeadRepository {

    private static final String WHY = "Leads are website sign-ups for the Zamu team, not tenant data";

    private final JdbcClient jdbc;

    LeadRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @CrossTenant(WHY)
    void insert(Lead lead) {
        jdbc.sql("""
                INSERT INTO leads (id, business_name, contact_name, phone, email, business_type, message, plan_code, status,
                                   consent_at, created_at, updated_at)
                VALUES (:id, :businessName, :contactName, :phone, :email, :businessType, :message, :planCode, :status,
                        :consentAt, :createdAt, :updatedAt)
                """)
                .param("id", Uuids.toBytes(lead.id()))
                .param("businessName", lead.businessName())
                .param("contactName", lead.contactName())
                .param("phone", lead.phone())
                .param("email", lead.email())
                .param("businessType", lead.businessType().dbValue())
                .param("message", lead.message())
                .param("planCode", lead.planCode())
                .param("status", lead.status().dbValue())
                .param("consentAt", ZamuTime.toDb(lead.consentAt()))
                .param("createdAt", ZamuTime.toDb(lead.createdAt()))
                .param("updatedAt", ZamuTime.toDb(lead.updatedAt()))
                .update();
    }

    @CrossTenant(WHY)
    Optional<Lead> findById(UUID id) {
        return jdbc.sql("SELECT " + LeadRowMapper.COLUMNS + " FROM leads WHERE id = :id")
                .param("id", Uuids.toBytes(id))
                .query(LeadRowMapper.INSTANCE)
                .optional();
    }

    @CrossTenant(WHY)
    List<Lead> findRecent(LeadStatus status, int limit) {
        if (status == null) {
            return jdbc.sql("SELECT " + LeadRowMapper.COLUMNS + " FROM leads ORDER BY created_at DESC, id DESC LIMIT :limit")
                    .param("limit", limit)
                    .query(LeadRowMapper.INSTANCE)
                    .list();
        }
        return jdbc.sql("SELECT " + LeadRowMapper.COLUMNS
                        + " FROM leads WHERE status = :status ORDER BY created_at DESC, id DESC LIMIT :limit")
                .param("status", status.dbValue())
                .param("limit", limit)
                .query(LeadRowMapper.INSTANCE)
                .list();
    }

    @CrossTenant(WHY)
    int updateStatus(UUID id, LeadStatus status, Instant now) {
        return jdbc.sql("UPDATE leads SET status = :status, updated_at = :now WHERE id = :id")
                .param("id", Uuids.toBytes(id))
                .param("status", status.dbValue())
                .param("now", ZamuTime.toDb(now))
                .update();
    }
}
