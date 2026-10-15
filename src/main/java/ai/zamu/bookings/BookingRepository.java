package ai.zamu.bookings;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.ids.Uuids;
import ai.zamu.shared.security.CrossTenant;
import ai.zamu.shared.time.ZamuTime;

/** The bookings table, read with its service and resource names. */
@Component
class BookingRepository {

    private static final String SELECT = """
            SELECT b.id, b.tenant_id, b.customer_id, b.customer_name, b.customer_phone, b.conversation_id,
                   b.service_id, s.name AS service_name, s.name_sw AS service_name_sw, s.deposit_amount,
                   b.resource_id, r.name AS resource_name, b.starts_at, b.ends_at, b.status, b.hold_expires_at,
                   b.calendar_event_id, b.source, b.notes, b.awaiting_reply, b.awaiting_until, b.created_at, b.updated_at
              FROM bookings b
              JOIN services s ON s.id = b.service_id
              LEFT JOIN resources r ON r.id = b.resource_id
            """;

    private static final String OPEN_STATUSES = "('held', 'pending_payment', 'confirmed')";

    private static final RowMapper<Booking> MAPPER = (rs, n) -> new Booking(
            Uuids.read(rs, "id"), Uuids.read(rs, "tenant_id"), Uuids.read(rs, "customer_id"),
            rs.getString("customer_name"), rs.getString("customer_phone"), Uuids.read(rs, "conversation_id"),
            Uuids.read(rs, "service_id"), rs.getString("service_name"), rs.getString("service_name_sw"),
            rs.getBigDecimal("deposit_amount"), Uuids.read(rs, "resource_id"), rs.getString("resource_name"),
            ZamuTime.read(rs, "starts_at"), ZamuTime.read(rs, "ends_at"), BookingStatus.fromDb(rs.getString("status")),
            ZamuTime.read(rs, "hold_expires_at"), rs.getString("calendar_event_id"), rs.getString("source"),
            rs.getString("notes"), rs.getString("awaiting_reply"), ZamuTime.read(rs, "awaiting_until"),
            ZamuTime.read(rs, "created_at"), ZamuTime.read(rs, "updated_at"));

    record NewBooking(UUID id, UUID customerId, String customerName, String customerPhone, UUID conversationId,
            UUID serviceId, UUID resourceId, Instant startsAt, Instant endsAt, BookingStatus status,
            Instant holdExpiresAt, String source, String notes) {
    }

    private final JdbcClient jdbc;

    BookingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void insert(TenantId tenantId, NewBooking b, Instant now) {
        jdbc.sql("""
                INSERT INTO bookings (id, tenant_id, customer_id, customer_name, customer_phone, conversation_id,
                                      service_id, resource_id, starts_at, ends_at, status, hold_expires_at, source, notes,
                                      created_at, updated_at)
                VALUES (:id, :tenantId, :customerId, :customerName, :customerPhone, :conversationId, :serviceId,
                        :resourceId, :startsAt, :endsAt, :status, :holdExpiresAt, :source, :notes, :now, :now)
                """)
                .param("id", Uuids.toBytes(b.id()))
                .param("tenantId", tenantId.bytes())
                .param("customerId", Uuids.toBytes(b.customerId()))
                .param("customerName", b.customerName())
                .param("customerPhone", b.customerPhone())
                .param("conversationId", Uuids.toBytes(b.conversationId()))
                .param("serviceId", Uuids.toBytes(b.serviceId()))
                .param("resourceId", Uuids.toBytes(b.resourceId()))
                .param("startsAt", ZamuTime.toDb(b.startsAt()))
                .param("endsAt", ZamuTime.toDb(b.endsAt()))
                .param("status", b.status().dbValue())
                .param("holdExpiresAt", b.holdExpiresAt() == null ? null : ZamuTime.toDb(b.holdExpiresAt()))
                .param("source", b.source())
                .param("notes", b.notes())
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    Optional<Booking> findById(TenantId tenantId, UUID id) {
        return jdbc.sql(SELECT + " WHERE b.tenant_id = :tenantId AND b.id = :id")
                .param("tenantId", tenantId.bytes())
                .param("id", Uuids.toBytes(id))
                .query(MAPPER)
                .optional();
    }

    /** Bookings that occupy time between {@code from} and {@code to}: held (not expired), pending or confirmed. */
    List<Booking> findBlocking(TenantId tenantId, Instant from, Instant to, Instant now) {
        return jdbc.sql(SELECT + """
                 WHERE b.tenant_id = :tenantId AND b.starts_at < :to AND b.ends_at > :from
                   AND b.status IN %s
                   AND (b.hold_expires_at IS NULL OR b.hold_expires_at > :now OR b.status = 'confirmed')
                """.formatted(OPEN_STATUSES))
                .param("tenantId", tenantId.bytes())
                .param("from", ZamuTime.toDb(from))
                .param("to", ZamuTime.toDb(to))
                .param("now", ZamuTime.toDb(now))
                .query(MAPPER)
                .list();
    }

    /** The customer's upcoming open bookings, soonest first. */
    List<Booking> findUpcomingForCustomer(TenantId tenantId, UUID customerId, Instant now) {
        return jdbc.sql(SELECT + """
                 WHERE b.tenant_id = :tenantId AND b.customer_id = :customerId AND b.starts_at > :now
                   AND b.status IN ('pending_payment', 'confirmed')
                 ORDER BY b.starts_at
                """)
                .param("tenantId", tenantId.bytes())
                .param("customerId", Uuids.toBytes(customerId))
                .param("now", ZamuTime.toDb(now))
                .query(MAPPER)
                .list();
    }

    List<Booking> findForCustomer(TenantId tenantId, UUID customerId, int limit) {
        return jdbc.sql(SELECT + " WHERE b.tenant_id = :tenantId AND b.customer_id = :customerId"
                        + " ORDER BY b.starts_at DESC LIMIT :limit")
                .param("tenantId", tenantId.bytes())
                .param("customerId", Uuids.toBytes(customerId))
                .param("limit", limit)
                .query(MAPPER)
                .list();
    }

    /** Staff list: bookings starting in [from, to), optionally without cancelled ones. */
    List<Booking> findBetween(TenantId tenantId, Instant from, Instant to, boolean includeCancelled) {
        return jdbc.sql(SELECT + " WHERE b.tenant_id = :tenantId AND b.starts_at >= :from AND b.starts_at < :to"
                        + (includeCancelled ? "" : " AND b.status <> 'cancelled'")
                        + " ORDER BY b.starts_at")
                .param("tenantId", tenantId.bytes())
                .param("from", ZamuTime.toDb(from))
                .param("to", ZamuTime.toDb(to))
                .query(MAPPER)
                .list();
    }

    int updateStatus(TenantId tenantId, UUID id, BookingStatus status, String cancelledBy, Instant now) {
        return jdbc.sql("""
                UPDATE bookings SET status = :status, cancelled_by = :cancelledBy, hold_expires_at = CASE
                       WHEN :status IN ('held', 'pending_payment') THEN hold_expires_at ELSE NULL END,
                       awaiting_reply = CASE WHEN :status IN ('confirmed') THEN awaiting_reply ELSE NULL END,
                       updated_at = :now
                 WHERE tenant_id = :tenantId AND id = :id
                """)
                .param("tenantId", tenantId.bytes())
                .param("id", Uuids.toBytes(id))
                .param("status", status.dbValue())
                .param("cancelledBy", cancelledBy)
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    int updateTimes(TenantId tenantId, UUID id, UUID resourceId, Instant startsAt, Instant endsAt, Instant now) {
        return jdbc.sql("""
                UPDATE bookings SET resource_id = :resourceId, starts_at = :startsAt, ends_at = :endsAt,
                       awaiting_reply = NULL, awaiting_until = NULL, updated_at = :now
                 WHERE tenant_id = :tenantId AND id = :id
                """)
                .param("tenantId", tenantId.bytes())
                .param("id", Uuids.toBytes(id))
                .param("resourceId", Uuids.toBytes(resourceId))
                .param("startsAt", ZamuTime.toDb(startsAt))
                .param("endsAt", ZamuTime.toDb(endsAt))
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    void setCalendarEvent(TenantId tenantId, UUID id, String eventId) {
        jdbc.sql("UPDATE bookings SET calendar_event_id = :eventId WHERE tenant_id = :tenantId AND id = :id")
                .param("tenantId", tenantId.bytes())
                .param("id", Uuids.toBytes(id))
                .param("eventId", eventId)
                .update();
    }

    void setAwaiting(TenantId tenantId, UUID id, String awaiting, Instant until) {
        jdbc.sql("""
                UPDATE bookings SET awaiting_reply = :awaiting, awaiting_until = :until
                 WHERE tenant_id = :tenantId AND id = :id
                """)
                .param("tenantId", tenantId.bytes())
                .param("id", Uuids.toBytes(id))
                .param("awaiting", awaiting)
                .param("until", until == null ? null : ZamuTime.toDb(until))
                .update();
    }

    /** The booking a numbered reply ("1", "2", "3") refers to: the latest confirmation or reminder still open. */
    Optional<Booking> findAwaiting(TenantId tenantId, UUID customerId, Instant now) {
        return jdbc.sql(SELECT + """
                 WHERE b.tenant_id = :tenantId AND b.customer_id = :customerId AND b.awaiting_reply IS NOT NULL
                   AND b.awaiting_until > :now AND b.status IN ('pending_payment', 'confirmed') AND b.starts_at > :now
                 ORDER BY b.updated_at DESC LIMIT 1
                """)
                .param("tenantId", tenantId.bytes())
                .param("customerId", Uuids.toBytes(customerId))
                .param("now", ZamuTime.toDb(now))
                .query(MAPPER)
                .optional();
    }

    /** Pending payment: the deposit window, extended when an M-Pesa request is sent. */
    void setHoldExpiry(TenantId tenantId, UUID id, Instant until) {
        jdbc.sql("UPDATE bookings SET hold_expires_at = :until WHERE tenant_id = :tenantId AND id = :id")
                .param("tenantId", tenantId.bytes())
                .param("id", Uuids.toBytes(id))
                .param("until", ZamuTime.toDb(until))
                .update();
    }

    @CrossTenant("Sweeper for expired holds across all tenants; each result carries its tenant_id")
    List<Booking> findExpiredHolds(Instant now, int limit) {
        return jdbc.sql(SELECT + """
                 WHERE b.status IN ('held', 'pending_payment') AND b.hold_expires_at <= :now
                 ORDER BY b.hold_expires_at LIMIT :limit
                """)
                .param("now", ZamuTime.toDb(now))
                .param("limit", limit)
                .query(MAPPER)
                .list();
    }

    /** BK-10: upcoming confirmed bookings that have a calendar event, to compare with the calendar. */
    List<Booking> findSyncCandidates(TenantId tenantId, Instant now, Instant until) {
        return jdbc.sql(SELECT + """
                 WHERE b.tenant_id = :tenantId AND b.status IN ('pending_payment', 'confirmed')
                   AND b.calendar_event_id IS NOT NULL AND b.starts_at > :now AND b.starts_at < :until
                 ORDER BY b.starts_at
                """)
                .param("tenantId", tenantId.bytes())
                .param("now", ZamuTime.toDb(now))
                .param("until", ZamuTime.toDb(until))
                .query(MAPPER)
                .list();
    }

    // --- privacy (NFR-11, NFR-12) ---

    int deleteForCustomer(TenantId tenantId, UUID customerId) {
        return jdbc.sql("DELETE FROM bookings WHERE tenant_id = :tenantId AND customer_id = :customerId")
                .param("tenantId", tenantId.bytes())
                .param("customerId", Uuids.toBytes(customerId))
                .update();
    }

    int deleteAllForTenant(TenantId tenantId) {
        return jdbc.sql("DELETE FROM bookings WHERE tenant_id = :tenantId").param("tenantId", tenantId.bytes()).update();
    }
}
