package ai.zamu.reports;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * REP-01 and REP-02 for one tenant and one week (Monday to Sunday in the tenant's timezone).
 *
 * @param handoverRatePercent share of the week's conversations that went to staff, rounded
 * @param medianFirstReplyMinutes median time from handover to the first staff reply; null with no replies
 * @param paymentsCollectedKes M-Pesa payments received; zero until payments are switched on (Milestone 6)
 * @param hoursConfigured false when opening hours aren't set, so nothing counts as after hours
 */
public record WeeklyReport(LocalDate weekStart, LocalDate weekEnd, int conversations, int afterHoursConversations,
        int handedOver, int handoverRatePercent, Integer medianFirstReplyMinutes, List<ServiceCount> bookingsByService,
        int bookings, int afterHoursBookings, int noShows, BigDecimal paymentsCollectedKes, List<TopQuestion> topQuestions,
        List<Unanswered> unanswered, boolean hoursConfigured) {

    public record ServiceCount(String service, int bookings) {
    }

    /** A knowledge base entry and how many replies used it (REP-02, ASSUMPTION: "top questions" by entry). */
    public record TopQuestion(String title, int answers) {
    }

    /** A customer message Zamu wasn't sure about (low-confidence handover), for "suggest KB entry". */
    public record Unanswered(String question, Instant askedAt) {
    }
}
