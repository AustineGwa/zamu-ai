package ai.zamu.leads;

import java.time.Instant;
import java.util.UUID;

/** A sign-up as the admin console shows it. */
public record LeadView(UUID id, String businessName, String contactName, String phone, String phoneDisplay,
        String email, BusinessType businessType, String message, String planCode, LeadStatus status, Instant createdAt) {
}
