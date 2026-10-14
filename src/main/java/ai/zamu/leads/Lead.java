package ai.zamu.leads;

import java.time.Instant;
import java.util.UUID;

import ai.zamu.shared.phone.KenyanPhone;

/** A leads row. */
record Lead(UUID id, String businessName, String contactName, String phone, String email, BusinessType businessType,
        String message, String planCode, LeadStatus status, Instant consentAt, Instant createdAt, Instant updatedAt) {

    LeadView toView() {
        return new LeadView(id, businessName, contactName, phone, KenyanPhone.display(phone), email, businessType,
                message, planCode, status, createdAt);
    }
}
