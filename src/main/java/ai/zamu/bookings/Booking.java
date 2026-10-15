package ai.zamu.bookings;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A booking with its service and resource names (spec: bookings). BK-14: only the customer's name, phone and the
 * service are collected; notes are optional.
 *
 * @param source "bot" (booked through Zamu) or "staff"
 */
public record Booking(UUID id, UUID tenantId, UUID customerId, String customerName, String customerPhone,
        UUID conversationId, UUID serviceId, String serviceName, String serviceNameSw, BigDecimal depositAmount,
        UUID resourceId, String resourceName, Instant startsAt, Instant endsAt, BookingStatus status,
        Instant holdExpiresAt, String calendarEventId, String source, String notes, String awaitingReply,
        Instant awaitingUntil, Instant createdAt, Instant updatedAt) {

    public String serviceName(String language) {
        return "sw".equals(language) && serviceNameSw != null && !serviceNameSw.isBlank() ? serviceNameSw : serviceName;
    }

    public boolean needsDeposit() {
        return depositAmount != null && depositAmount.signum() > 0;
    }

    /** A short code for M-Pesa AccountReference and for staff (spec: PAY step 2): the last 6 hex digits. */
    public String shortCode() {
        String hex = id.toString().replace("-", "");
        return hex.substring(hex.length() - 6).toUpperCase(java.util.Locale.ROOT);
    }
}
