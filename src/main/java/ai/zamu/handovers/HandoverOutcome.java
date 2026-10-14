package ai.zamu.handovers;

import java.time.ZonedDateTime;

/**
 * What the engine should tell the customer after a handover (HO-02): the normal handover message while staff
 * are on duty, otherwise the after-hours message with the next time staff are back.
 *
 * @param nextStaffTime null when staff are on duty now, or when no future handover hours are configured
 */
public record HandoverOutcome(boolean staffOnDuty, ZonedDateTime nextStaffTime) {
}
