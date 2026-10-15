package ai.zamu.bookings;

/**
 * Per-tenant booking rules (BK-04 notice and advance, BK-08 change cut-off, BK-11 optional 2-hour reminder).
 *
 * @param slotStepMin how often slots start; ASSUMPTION: 30 minutes by default
 */
public record BookingSettings(int minNoticeMin, int maxAdvanceDays, int changeCutoffMin, boolean reminder2h,
        int slotStepMin) {

    public static final BookingSettings DEFAULTS = new BookingSettings(120, 30, 240, false, 30);
}
