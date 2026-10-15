package ai.zamu.knowledge;

import java.time.Instant;
import java.util.UUID;

/** A knowledge base entry as pages show it. */
public record KbEntryView(UUID id, String title, String question, String answerEn, String answerSw,
        KbEntryStatus status, Instant approvedAt, String sourceRef, Instant updatedAt) {

    public boolean hasSwahili() {
        return answerSw != null && !answerSw.isBlank();
    }

    /** Operations Manual, writing rule 1: entries under 80 words. */
    public int answerWords() {
        return answerEn == null || answerEn.isBlank() ? 0 : answerEn.strip().split("\\s+").length;
    }
}
