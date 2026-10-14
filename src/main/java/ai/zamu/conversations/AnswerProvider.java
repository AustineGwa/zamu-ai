package ai.zamu.conversations;

import java.util.UUID;

import ai.zamu.handovers.HandoverReason;
import ai.zamu.shared.ids.TenantId;

/**
 * Writes Zamu's reply to a customer question. Milestone 3 implements this with retrieval, the AI model and the
 * guardrails (AI-01 to AI-08). Until then {@link NoAiYetAnswerProvider} hands every question to staff.
 */
public interface AnswerProvider {

    Answer answer(Question question);

    record Question(TenantId tenantId, UUID conversationId, UUID customerId, String text, String language) {
    }

    /**
     * @param handover null when no handover is needed; otherwise the reason (for example LOW_CONFIDENCE, AI-03)
     */
    record Answer(String text, HandoverReason handover) {
    }
}
