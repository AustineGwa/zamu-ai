package ai.zamu.ai;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import ai.zamu.knowledge.Retriever.RetrievedChunk;

import tools.jackson.databind.ObjectMapper;

/**
 * Used when no AI provider is configured (local h2 profile, automated tests): answers with the best-matching
 * approved entry, word for word, in the same JSON shape a real model returns, so the whole pipeline, guardrails
 * included, runs without an API key. Not for real customers: it can't rephrase, combine entries or classify intent.
 */
@Component
class OfflineAiClient {

    static final String MODEL = "offline";

    private final ObjectMapper objectMapper;

    OfflineAiClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    AiClient.AiCompletion complete(AiClient.AiRequest request) {
        Map<String, Object> out = new LinkedHashMap<>();
        String intent = intent(request.question());
        if (intent != null) { // so bookings can be tried locally (BK-03) without a model
            out.put("reply", "");
            out.put("language", request.language());
            out.put("intent", intent);
            out.put("confidence", 0.9);
            out.put("used_entry_ids", List.of());
            return new AiClient.AiCompletion(objectMapper.writeValueAsString(out), MODEL, 0, 0);
        }
        RetrievedChunk best = request.chunks().stream()
                .sorted(Comparator.comparing((RetrievedChunk c) -> !c.language().equals(request.language()))
                        .thenComparing(Comparator.comparingDouble(RetrievedChunk::score).reversed()))
                .findFirst().orElse(null);
        if (best == null) {
            out.put("reply", "");
            out.put("language", request.language());
            out.put("intent", "question");
            out.put("confidence", 0.0);
            out.put("used_entry_ids", List.of());
        } else {
            out.put("reply", answerPart(best.content()));
            out.put("language", best.language());
            out.put("intent", "question");
            out.put("confidence", Math.min(0.95, 0.5 + best.score() / 2));
            out.put("used_entry_ids", List.of(best.entryId().toString()));
        }
        return new AiClient.AiCompletion(objectMapper.writeValueAsString(out), MODEL, 0, 0);
    }

    /** Rough intent from keywords, standing in for the model's classification (MSG-07). */
    static String intent(String question) {
        String q = question == null ? "" : question.toLowerCase(java.util.Locale.ROOT);
        if (q.matches(".*\\b(reschedule|change my (booking|appointment)|move my (booking|appointment)|badilisha)\\b.*")) {
            return "reschedule";
        }
        if (q.matches(".*\\b(cancel|ghairi)\\w*.*")) {
            return "cancel";
        }
        if (q.matches(".*\\b(book|reserve|make an appointment|weka miadi|kuweka miadi|panga miadi)\\b.*")) {
            return "book";
        }
        return null;
    }

    /** A chunk is "title, question, answer": the answer is everything after the second line. */
    private static String answerPart(String content) {
        String[] lines = content.split("\\R", 3);
        return lines.length == 3 ? lines[2].strip() : content.strip();
    }
}
