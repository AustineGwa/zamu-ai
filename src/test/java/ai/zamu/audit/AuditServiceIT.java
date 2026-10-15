package ai.zamu.audit;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.ids.UuidV7;
import ai.zamu.support.AbstractMySqlIT;

import static org.assertj.core.api.Assertions.assertThat;

class AuditServiceIT extends AbstractMySqlIT {

    @Autowired
    AuditService audit;

    @Test
    void NFR_14_recordsActionWithBeforeAndAfterAsJson() {
        TenantId tenant = TenantId.of(UuidV7.next());
        audit.record(AuditEvent.of(tenant, "settings.changed", "tenant", tenant.value())
                .withBefore(Map.of("tone", "friendly")).withAfter(Map.of("tone", "formal")));

        String after = jdbc.sql("SELECT after_state FROM audit_logs").query(String.class).single();
        assertThat(after).contains("formal");
        assertThat(audit.recentForTenant(tenant, 10)).extracting(AuditEntryView::action).containsExactly("settings.changed");
    }

    @Test
    void NFR_09_recentForTenant_returnsOnlyThatTenantsEntries() {
        TenantId a = TenantId.of(UuidV7.next());
        TenantId b = TenantId.of(UuidV7.next());
        audit.record(AuditEvent.of(a, "a.thing", "tenant", a.value()));
        audit.record(AuditEvent.of(b, "b.thing", "tenant", b.value()));

        assertThat(audit.recentForTenant(a, 10)).extracting(AuditEntryView::action).containsExactly("a.thing");
    }

    @Test
    void NFR_14_adminSearchFiltersByClientActionAndDates() {
        TenantId a = TenantId.of(UuidV7.next());
        TenantId b = TenantId.of(UuidV7.next());
        java.time.Instant day1 = clock.instant();
        audit.record(AuditEvent.of(a, "kb.approved", "kb_entry", null));
        audit.record(AuditEvent.of(b, "kb.approved", "kb_entry", null));
        audit.record(AuditEvent.of(null, "usage.exported", "usage", null));
        clock.advance(java.time.Duration.ofDays(3));
        audit.record(AuditEvent.of(a, "booking.created", "booking", null));
        java.time.Instant end = clock.instant().plusSeconds(1);

        java.util.List<AuditSearchRow> all = audit.search(null, null, day1, end);
        assertThat(all).extracting(AuditSearchRow::action)
                .containsExactlyInAnyOrder("booking.created", "usage.exported", "kb.approved", "kb.approved");
        assertThat(all.getFirst().action()).isEqualTo("booking.created"); // newest first
        assertThat(audit.search(a.value(), null, day1, end)).extracting(AuditSearchRow::action)
                .containsExactly("booking.created", "kb.approved");
        assertThat(audit.search(null, "kb.", day1, end)).hasSize(2);
        assertThat(audit.search(null, null, day1.plus(java.time.Duration.ofDays(1)), end)).extracting(AuditSearchRow::action)
                .containsExactly("booking.created");
        // "_" and "%" are matched literally, not as wildcards.
        assertThat(audit.search(null, "%", day1, end)).isEmpty();
        assertThat(audit.search(null, "kb_", day1, end)).isEmpty();
    }
}
