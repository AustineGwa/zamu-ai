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
        String emergencyNumber,
        String handoverTopics,
        Instant createdAt) {

    /** Zamu replies to customers only while the tenant is onboarding (testing) or live (MSG-09). */
    /** HO-01: words or phrases that always go to a person, one per line. */
    public java.util.List<String> handoverTopicList() {
        if (handoverTopics == null || handoverTopics.isBlank()) {
            return java.util.List.of();
        }
        return handoverTopics.lines().map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    public boolean botActive() {
        return status == TenantStatus.ONBOARDING || status == TenantStatus.LIVE;
    }
}
