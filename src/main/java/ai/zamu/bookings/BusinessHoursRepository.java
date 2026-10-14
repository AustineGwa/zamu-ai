package ai.zamu.bookings;

import java.sql.Time;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.ids.UuidV7;
import ai.zamu.shared.ids.Uuids;
import ai.zamu.shared.time.ZamuTime;

/** The business_hours table: one row per tenant and weekday. */
@Component
class BusinessHoursRepository {

    private final JdbcClient jdbc;

    BusinessHoursRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<DayHours> findWeek(TenantId tenantId) {
        return jdbc.sql("""
                SELECT day_of_week, opens_at, closes_at, handover_opens_at, handover_closes_at
                  FROM business_hours
                 WHERE tenant_id = :tenantId
                 ORDER BY day_of_week
                """)
                .param("tenantId", tenantId.bytes())
                .query((rs, rowNum) -> new DayHours(
                        DayOfWeek.of(rs.getInt("day_of_week")),
                        rs.getObject("opens_at", LocalTime.class),
                        rs.getObject("closes_at", LocalTime.class),
                        rs.getObject("handover_opens_at", LocalTime.class),
                        rs.getObject("handover_closes_at", LocalTime.class)))
                .list();
    }

    void replaceWeek(TenantId tenantId, List<DayHours> week, Instant now) {
        jdbc.sql("DELETE FROM business_hours WHERE tenant_id = :tenantId")
                .param("tenantId", tenantId.bytes())
                .update();
        for (DayHours day : week) {
            jdbc.sql("""
                    INSERT INTO business_hours (id, tenant_id, day_of_week, opens_at, closes_at, handover_opens_at,
                                                handover_closes_at, created_at, updated_at)
                    VALUES (:id, :tenantId, :day, :opensAt, :closesAt, :handoverOpensAt, :handoverClosesAt, :now, :now)
                    """)
                    .param("id", Uuids.toBytes(UuidV7.next()))
                    .param("tenantId", tenantId.bytes())
                    .param("day", day.day().getValue())
                    .param("opensAt", sql(day.opensAt()))
                    .param("closesAt", sql(day.closesAt()))
                    .param("handoverOpensAt", sql(day.handoverOpensAt()))
                    .param("handoverClosesAt", sql(day.handoverClosesAt()))
                    .param("now", ZamuTime.toDb(now))
                    .update();
        }
    }

    private static Time sql(LocalTime value) {
        return value == null ? null : Time.valueOf(value);
    }
}
