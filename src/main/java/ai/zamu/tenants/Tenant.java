package ai.zamu.tenants;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import ai.zamu.shared.ids.TenantId;

/** A tenants row. */
record Tenant(
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
        String emergencyNumber,
        String handoverTopics,
        Instant createdAt,
        Instant updatedAt) {

    TenantId tenantId() {
        return TenantId.of(id);
    }

    TenantView toView() {
        return new TenantView(id, name, displayName, plan, status, timezone, defaultLanguage, tone,
                conversationLimit, confidenceThreshold, holdingReply, emergencyNumber, handoverTopics, createdAt);
    }
}
