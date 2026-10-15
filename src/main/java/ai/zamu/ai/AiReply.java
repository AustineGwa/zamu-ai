package ai.zamu.ai;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The model's structured output (spec: AI answering, step 3): { reply, language, intent, confidence,
 * used_entry_ids }. Tool calls arrive with bookings and payments (Milestones 5 and 6).
 */
record AiReply(String reply, String language, String intent, double confidence, List<UUID> usedEntryIds) {

    /** MSG-07 intents. */
    static final Set<String> INTENTS = Set.of("question", "book", "reschedule", "cancel", "pay", "talk_to_person",
            "greeting", "emergency", "other");

    boolean isQuestion() {
        return "question".equals(intent);
    }

    boolean isBookingOrPayment() {
        return Set.of("book", "reschedule", "cancel", "pay").contains(intent);
    }

    boolean isSmallTalk() {
        return "greeting".equals(intent) || "other".equals(intent);
    }
}
