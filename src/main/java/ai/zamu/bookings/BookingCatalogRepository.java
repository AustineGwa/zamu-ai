package ai.zamu.bookings;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.ids.Uuids;
import ai.zamu.shared.time.ZamuTime;

/** The services, resources, service_resources, booking_settings and holidays tables. */
@Component
class BookingCatalogRepository {

    private static final String SERVICE_COLUMNS = """
            id, name, name_sw, description, duration_min, price_min, price_max, deposit_amount, buffer_min, active""";

    private final JdbcClient jdbc;

    BookingCatalogRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // --- services ---

    List<BookableService> findServices(TenantId tenantId, boolean activeOnly) {
        Map<UUID, List<UUID>> links = links(tenantId);
        return jdbc.sql("SELECT " + SERVICE_COLUMNS + " FROM services WHERE tenant_id = :tenantId"
                        + (activeOnly ? " AND active = TRUE" : "") + " ORDER BY name")
                .param("tenantId", tenantId.bytes())
                .query((rs, n) -> readService(rs))
                .list()
                .stream()
                .map(s -> withResources(s, links.getOrDefault(s.id(), List.of())))
                .toList();
    }

    Optional<BookableService> findService(TenantId tenantId, UUID id) {
        return jdbc.sql("SELECT " + SERVICE_COLUMNS + " FROM services WHERE tenant_id = :tenantId AND id = :id")
                .param("tenantId", tenantId.bytes())
                .param("id", Uuids.toBytes(id))
                .query((rs, n) -> readService(rs))
                .optional()
                .map(s -> withResources(s, links(tenantId).getOrDefault(s.id(), List.of())));
    }

    void insertService(TenantId tenantId, UUID id, ServiceForm f, Instant now) {
        jdbc.sql("""
                INSERT INTO services (id, tenant_id, name, name_sw, description, duration_min, price_min, price_max,
                                      deposit_amount, buffer_min, active, created_at, updated_at)
                VALUES (:id, :tenantId, :name, :nameSw, :description, :duration, :priceMin, :priceMax, :deposit,
                        :buffer, :active, :now, :now)
                """)
                .param("id", Uuids.toBytes(id))
                .param("tenantId", tenantId.bytes())
                .params(serviceParams(f))
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    int updateService(TenantId tenantId, UUID id, ServiceForm f, Instant now) {
        return jdbc.sql("""
                UPDATE services SET name = :name, name_sw = :nameSw, description = :description,
                       duration_min = :duration, price_min = :priceMin, price_max = :priceMax,
                       deposit_amount = :deposit, buffer_min = :buffer, active = :active, updated_at = :now
                 WHERE tenant_id = :tenantId AND id = :id
                """)
                .param("id", Uuids.toBytes(id))
                .param("tenantId", tenantId.bytes())
                .params(serviceParams(f))
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    void replaceServiceResources(TenantId tenantId, UUID serviceId, List<UUID> resourceIds) {
        jdbc.sql("DELETE FROM service_resources WHERE tenant_id = :tenantId AND service_id = :serviceId")
                .param("tenantId", tenantId.bytes())
                .param("serviceId", Uuids.toBytes(serviceId))
                .update();
        for (UUID resourceId : resourceIds) {
            jdbc.sql("""
                    INSERT INTO service_resources (tenant_id, service_id, resource_id)
                    VALUES (:tenantId, :serviceId, :resourceId)
                    """)
                    .param("tenantId", tenantId.bytes())
                    .param("serviceId", Uuids.toBytes(serviceId))
                    .param("resourceId", Uuids.toBytes(resourceId))
                    .update();
        }
    }

    private Map<UUID, List<UUID>> links(TenantId tenantId) {
        Map<UUID, List<UUID>> links = new LinkedHashMap<>();
        jdbc.sql("""
                SELECT sr.service_id, sr.resource_id FROM service_resources sr
                  JOIN resources r ON r.id = sr.resource_id
                 WHERE sr.tenant_id = :tenantId ORDER BY r.name
                """)
                .param("tenantId", tenantId.bytes())
                .query(rs -> {
                    links.computeIfAbsent(Uuids.read(rs, "service_id"), k -> new ArrayList<>())
                            .add(Uuids.read(rs, "resource_id"));
                });
        return links;
    }

    private static Map<String, Object> serviceParams(ServiceForm f) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("name", f.name().strip());
        p.put("nameSw", blankToNull(f.nameSw()));
        p.put("description", blankToNull(f.description()));
        p.put("duration", f.durationMin());
        p.put("priceMin", f.priceMin());
        p.put("priceMax", f.priceMax());
        p.put("deposit", f.depositAmount() == null ? BigDecimal.ZERO : f.depositAmount());
        p.put("buffer", f.bufferMin() == null ? 0 : f.bufferMin());
        p.put("active", f.active());
        return p;
    }

    private static BookableService readService(ResultSet rs) throws SQLException {
        return new BookableService(Uuids.read(rs, "id"), rs.getString("name"), rs.getString("name_sw"),
                rs.getString("description"), rs.getInt("duration_min"), rs.getBigDecimal("price_min"),
                rs.getBigDecimal("price_max"), rs.getBigDecimal("deposit_amount"), rs.getInt("buffer_min"),
                rs.getBoolean("active"), List.of());
    }

    private static BookableService withResources(BookableService s, List<UUID> resourceIds) {
        return new BookableService(s.id(), s.name(), s.nameSw(), s.description(), s.durationMin(), s.priceMin(),
                s.priceMax(), s.depositAmount(), s.bufferMin(), s.active(), resourceIds);
    }

    // --- resources ---

    List<BookingResource> findResources(TenantId tenantId, boolean activeOnly) {
        return jdbc.sql("SELECT id, name, role, calendar_id, active FROM resources WHERE tenant_id = :tenantId"
                        + (activeOnly ? " AND active = TRUE" : "") + " ORDER BY name")
                .param("tenantId", tenantId.bytes())
                .query((rs, n) -> new BookingResource(Uuids.read(rs, "id"), rs.getString("name"), rs.getString("role"),
                        rs.getString("calendar_id"), rs.getBoolean("active")))
                .list();
    }

    void insertResource(TenantId tenantId, UUID id, ResourceForm f, Instant now) {
        jdbc.sql("""
                INSERT INTO resources (id, tenant_id, name, role, calendar_id, active, created_at, updated_at)
                VALUES (:id, :tenantId, :name, :role, :calendarId, :active, :now, :now)
                """)
                .param("id", Uuids.toBytes(id))
                .param("tenantId", tenantId.bytes())
                .param("name", f.name().strip())
                .param("role", blankToNull(f.role()))
                .param("calendarId", blankToNull(f.calendarId()))
                .param("active", f.active())
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    int updateResource(TenantId tenantId, UUID id, ResourceForm f, Instant now) {
        return jdbc.sql("""
                UPDATE resources SET name = :name, role = :role, calendar_id = :calendarId, active = :active,
                       updated_at = :now
                 WHERE tenant_id = :tenantId AND id = :id
                """)
                .param("id", Uuids.toBytes(id))
                .param("tenantId", tenantId.bytes())
                .param("name", f.name().strip())
                .param("role", blankToNull(f.role()))
                .param("calendarId", blankToNull(f.calendarId()))
                .param("active", f.active())
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    // --- settings ---

    Optional<BookingSettings> findSettings(TenantId tenantId) {
        return jdbc.sql("""
                SELECT min_notice_min, max_advance_days, change_cutoff_min, reminder_2h, slot_step_min
                  FROM booking_settings WHERE tenant_id = :tenantId
                """)
                .param("tenantId", tenantId.bytes())
                .query((rs, n) -> new BookingSettings(rs.getInt("min_notice_min"), rs.getInt("max_advance_days"),
                        rs.getInt("change_cutoff_min"), rs.getBoolean("reminder_2h"), rs.getInt("slot_step_min")))
                .optional();
    }

    /**
     * Locks the tenant's settings row for the rest of the transaction, creating it with the defaults first if
     * needed. Every hold and move takes this lock, so two customers can't take the same slot at once (BK-05).
     */
    void lockTenant(TenantId tenantId, Instant now) {
        if (findSettings(tenantId).isEmpty()) {
            try {
                saveSettings(tenantId, BookingSettings.DEFAULTS, now);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // created by a concurrent first booking
            }
        }
        jdbc.sql("SELECT tenant_id FROM booking_settings WHERE tenant_id = :tenantId FOR UPDATE")
                .param("tenantId", tenantId.bytes())
                .query((rs, n) -> 1)
                .list();
    }

    void saveSettings(TenantId tenantId, BookingSettings s, Instant now) {
        int updated = jdbc.sql("""
                UPDATE booking_settings SET min_notice_min = :notice, max_advance_days = :advance,
                       change_cutoff_min = :cutoff, reminder_2h = :reminder2h, slot_step_min = :step, updated_at = :now
                 WHERE tenant_id = :tenantId
                """)
                .params(settingsParams(tenantId, s, now))
                .update();
        if (updated == 0) {
            jdbc.sql("""
                    INSERT INTO booking_settings (tenant_id, min_notice_min, max_advance_days, change_cutoff_min,
                                                  reminder_2h, slot_step_min, updated_at)
                    VALUES (:tenantId, :notice, :advance, :cutoff, :reminder2h, :step, :now)
                    """)
                    .params(settingsParams(tenantId, s, now))
                    .update();
        }
    }

    private static Map<String, Object> settingsParams(TenantId tenantId, BookingSettings s, Instant now) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("tenantId", tenantId.bytes());
        p.put("notice", s.minNoticeMin());
        p.put("advance", s.maxAdvanceDays());
        p.put("cutoff", s.changeCutoffMin());
        p.put("reminder2h", s.reminder2h());
        p.put("step", s.slotStepMin());
        p.put("now", ZamuTime.toDb(now));
        return p;
    }

    // --- holidays ---

    List<Holiday> findHolidays(TenantId tenantId, LocalDate from) {
        return jdbc.sql("""
                SELECT id, holiday_date, closed, note FROM holidays
                 WHERE tenant_id = :tenantId AND holiday_date >= :from ORDER BY holiday_date
                """)
                .param("tenantId", tenantId.bytes())
                .param("from", from)
                .query((rs, n) -> new Holiday(Uuids.read(rs, "id"), rs.getObject("holiday_date", LocalDate.class),
                        rs.getBoolean("closed"), rs.getString("note")))
                .list();
    }

    boolean holidayExists(TenantId tenantId, LocalDate day) {
        return jdbc.sql("SELECT COUNT(*) FROM holidays WHERE tenant_id = :tenantId AND holiday_date = :day")
                .param("tenantId", tenantId.bytes())
                .param("day", day)
                .query(Integer.class)
                .single() > 0;
    }

    void insertHoliday(TenantId tenantId, UUID id, LocalDate day, String note, Instant now) {
        jdbc.sql("""
                INSERT INTO holidays (id, tenant_id, holiday_date, closed, note, created_at)
                VALUES (:id, :tenantId, :day, TRUE, :note, :now)
                """)
                .param("id", Uuids.toBytes(id))
                .param("tenantId", tenantId.bytes())
                .param("day", day)
                .param("note", blankToNull(note))
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    int deleteHoliday(TenantId tenantId, UUID id) {
        return jdbc.sql("DELETE FROM holidays WHERE tenant_id = :tenantId AND id = :id")
                .param("tenantId", tenantId.bytes())
                .param("id", Uuids.toBytes(id))
                .update();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    /** NFR-11: offboarding purge of services, resources, rules, closed days and the calendar connection. */
    void deleteAllForTenant(TenantId tenantId) {
        for (String table : new String[] {"service_resources", "services", "resources", "holidays", "booking_settings",
                "calendar_connections"}) {
            jdbc.sql("DELETE FROM " + table + " WHERE tenant_id = :tenantId").param("tenantId", tenantId.bytes()).update();
        }
    }
}
