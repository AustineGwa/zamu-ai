package ai.zamu.knowledge;

import java.time.Instant;
import java.util.UUID;

/** A kb_entries row: one question and its approved answer, in English and optionally Swahili. */
record KbEntry(UUID id, UUID tenantId, String title, String question, String answerEn, String answerSw,
        KbEntryStatus status, UUID approvedBy, Instant approvedAt, String sourceRef, Instant createdAt,
        Instant updatedAt) {

    KbEntryView toView() {
        return new KbEntryView(id, title, question, answerEn, answerSw, status, approvedAt, sourceRef, updatedAt);
    }
}
