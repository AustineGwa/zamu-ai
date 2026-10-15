package ai.zamu.bookings;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Booking rules (BK-04, BK-08, BK-11), in the units staff think in. */
public record SettingsForm(
        @NotNull @Min(value = 0, message = "Can't be negative") @Max(value = 168, message = "At most a week") Integer minNoticeHours,
        @NotNull @Min(value = 1, message = "At least 1 day") @Max(value = 365) Integer maxAdvanceDays,
        @NotNull @Min(value = 0) @Max(value = 168) Integer changeCutoffHours,
        boolean reminder2h,
        @NotNull @Min(value = 5, message = "At least 5 minutes") @Max(value = 240) Integer slotStepMin) {

    static SettingsForm from(BookingSettings s) {
        return new SettingsForm(s.minNoticeMin() / 60, s.maxAdvanceDays(), s.changeCutoffMin() / 60, s.reminder2h(),
                s.slotStepMin());
    }

    BookingSettings toSettings() {
        return new BookingSettings(minNoticeHours * 60, maxAdvanceDays, changeCutoffHours * 60, reminder2h, slotStepMin);
    }
}
