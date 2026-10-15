package ai.zamu.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads the model's JSON reply. Anything that isn't valid JSON of the agreed shape is rejected, which triggers the
 * one retry with the larger model (AI-09).
 */
@Component
class AiReplyParser {

    private final ObjectMapper objectMapper;

    AiReplyParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    Optional<AiReply> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return Optional.empty();
        }
        JsonNode node;
        try {
            node = objectMapper.readTree(text.substring(start, end + 1));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
        String intent = node.path("intent").asString("").toLowerCase(Locale.ROOT).strip();
        String language = node.path("language").asString("").toLowerCase(Locale.ROOT).strip();
        JsonNode confidenceNode = node.path("confidence");
        if (!AiReply.INTENTS.contains(intent) || !confidenceNode.isNumber()) {
            return Optional.empty();
        }
        double confidence = confidenceNode.asDouble(0);
        if (confidence < 0 || confidence > 1) {
            return Optional.empty();
        }
        List<UUID> ids = new ArrayList<>();
        for (JsonNode id : node.path("used_entry_ids")) {
            try {
                ids.add(UUID.fromString(id.asString("").strip()));
            } catch (IllegalArgumentException e) {
                return Optional.empty(); // an invented ID is a validation failure
            }
        }
        String reply = node.path("reply").asString("").strip();
        return Optional.of(new AiReply(reply, language.startsWith("sw") ? "sw" : "en", intent, confidence, ids));
    }
}
