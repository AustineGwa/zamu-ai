package ai.zamu.conversations;

import org.springframework.stereotype.Component;

import ai.zamu.handovers.HandoverReason;

/**
 * Milestone 2 stand-in for the AI step: every question gets the AI-03 "not sure" reply and goes to staff, so the
 * inbox and handover flow work end to end before AI answers arrive (Milestone 3 replaces this class).
 */
//@Component
class NoAiYetAnswerProvider implements AnswerProvider {

    @Override
    public Answer answer(Question question) {
        return null;
//        return new Answer(StandardMessages.notSure(question.language()), HandoverReason.LOW_CONFIDENCE);
    }
}
