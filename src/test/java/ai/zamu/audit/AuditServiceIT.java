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
}
