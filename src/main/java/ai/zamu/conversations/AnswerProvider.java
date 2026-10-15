package ai.zamu.conversations;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import ai.zamu.handovers.HandoverReason;
import ai.zamu.shared.ids.TenantId;

/**
 * The AI step (spec: AI answering). Implemented by the ai package; declared here so conversations never depends
 * on it. It says what kind of answer to give; the engine sends it, using the standard wording (Appendix B) for
 * everything except a grounded reply.
 */
public interface AnswerProvider {

    Answer answer(Question question);

    /**
     * @param language the customer's language, "en" or "sw"
     * @param history  earlier messages of this conversation, oldest first, without the question itself
     * @param replyId  the ID of the option the customer tapped (WhatsApp list or button reply), or null
     * @param customerPhone the customer's number in international digits, for bookings (BK-14)
     * @param customerName  the customer's profile name, if known
     */
    record Question(TenantId tenantId, UUID conversationId, UUID customerId, String text, String language,
            List<Turn> history, String replyId, String customerPhone, String customerName) {

        public Question {
            history = history == null ? List.of() : List.copyOf(history);
        }

        /** A question without a tapped option or customer details (admin preview, evaluation, tests). */
        public Question(TenantId tenantId, UUID conversationId, UUID customerId, String text, String language,
                List<Turn> history) {
            this(tenantId, conversationId, customerId, text, language, history, null, null, null);
        }
    }

    /** One earlier message: who said it ("customer", "zamu" or "staff") and what. */
    record Turn(String role, String text) {
    }

    enum Kind {
        /** A grounded reply to send as written. */
        REPLY,
        /** AI-03: the "not sure" reply and a low-confidence handover. */
        NOT_SURE,
        /** AI-04: the emergency reply, an immediate handover and an owner alert. */
        EMERGENCY,
        /** Hand over with the standard handover (or after-hours) message, for {@link Answer#handover()}. */
        HANDOVER
    }

    /**
     * @param text       the reply, for {@link Kind#REPLY} only
     * @param handover   the reason, for {@link Kind#HANDOVER}
     * @param confidence the model's confidence, stored on the message (spec: messages.ai_confidence)
     * @param entryIds   the knowledge base entries the reply used (spec: messages.kb_entry_ids)
     * @param options    for {@link Kind#REPLY}: options the customer taps (a WhatsApp list or buttons), or null
     */
    record Answer(Kind kind, String text, HandoverReason handover, BigDecimal confidence, List<UUID> entryIds,
            Interactive options) {

        public Answer {
            entryIds = entryIds == null ? List.of() : List.copyOf(entryIds);
        }

        public static Answer reply(String text, BigDecimal confidence, List<UUID> entryIds) {
            return new Answer(Kind.REPLY, text, null, confidence, entryIds, null);
        }

        /** A reply with options to tap, such as free slots (BK-03). Not from the knowledge base, so no sources. */
        public static Answer choices(String text, Interactive options) {
            return new Answer(Kind.REPLY, text, null, null, List.of(), options);
        }

        public static Answer notSure(BigDecimal confidence) {
            return new Answer(Kind.NOT_SURE, null, HandoverReason.LOW_CONFIDENCE, confidence, List.of(), null);
        }

        public static Answer emergency() {
            return new Answer(Kind.EMERGENCY, null, HandoverReason.SENSITIVE, null, List.of(), null);
        }

        public static Answer handover(HandoverReason reason) {
            return new Answer(Kind.HANDOVER, null, reason, null, List.of(), null);
        }

        /** Says {@code text} first (for example the deposit amount), then hands over as usual. */
        public static Answer handover(HandoverReason reason, String text) {
            return new Answer(Kind.HANDOVER, text, reason, null, List.of(), null);
        }
    }
}
