package ai.zamu.tenants;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A tenant as other packages and pages see it. */
public record TenantView(
        UUID id,
        String name,
        String displayName,
        TenantPlan plan,
        TenantStatus status,
        String timezone,
        TenantLanguage defaultLanguage,
        TenantTone tone,
        int conversationLimit,
        BigDecimal confidenceThreshold,
        String holdingReply,
        Instant createdAt) {

    /** Zamu replies to customers only while the tenant is onboarding (testing) or live (MSG-09). */
    public boolean botActive() {
        return status == TenantStatus.ONBOARDING || status == TenantStatus.LIVE;
    }
}
