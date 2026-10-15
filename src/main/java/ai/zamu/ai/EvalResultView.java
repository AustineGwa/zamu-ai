package ai.zamu.ai;

import java.math.BigDecimal;
import java.util.UUID;

/** One question's outcome in a run, shown next to the expected answer. */
public record EvalResultView(UUID caseId, EvalCaseKind kind, String language, String question, String expectedAnswer,
        String mustInclude, String reply, boolean handedOver, BigDecimal confidence, boolean passed, String reason) {
}
