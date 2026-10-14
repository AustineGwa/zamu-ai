package ai.zamu.reports;

import java.time.LocalDate;

/** One tenant's usage for one Nairobi calendar day. */
public record DailyUsage(LocalDate date, int conversations, int messagesIn, int messagesOut) {
}
