package ai.zamu.ai;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** An eval_cases row. */
public record EvalCase(UUID id, EvalCaseKind kind, String language, String question, String expectedAnswer,
        String mustInclude, boolean active) {

    /** Required facts, separated by ";" (e.g. "KES 4,500; KES 8,000"). */
    public List<String> requiredFacts() {
        if (mustInclude == null || mustInclude.isBlank()) {
            return List.of();
        }
        return Arrays.stream(mustInclude.split(";")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }
}
