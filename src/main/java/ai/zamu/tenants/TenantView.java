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
        Instant createdAt) {
}
