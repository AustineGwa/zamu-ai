package ai.zamu.knowledge;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The configured Spring AI embedding model, if any (spring.ai.model.embedding). Without one, search is keyword
 * only. Failures never break a reply: the caller falls back to keywords.
 */
@Component
class Embeddings {

    private static final Logger log = LoggerFactory.getLogger(Embeddings.class);

    private final ObjectProvider<EmbeddingModel> model;
    private final String label;

    Embeddings(ObjectProvider<EmbeddingModel> model, @Value("${spring.ai.model.embedding:none}") String provider,
            @Value("${spring.ai.openai.embedding.model:}") String openAiModel) {
        this.model = model;
        this.label = "openai".equals(provider) ? provider + ":" + openAiModel : provider;
    }

    boolean available() {
        return model.getIfAvailable() != null;
    }

    /** Stored with each chunk, so vectors from a different model are never compared. */
    String label() {
        return label;
    }

    Optional<float[]> embed(String text) {
        EmbeddingModel m = model.getIfAvailable();
        if (m == null || text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(m.embed(text));
        } catch (RuntimeException e) {
            log.warn("Embedding failed ({}); using keyword search only", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** One vector per text, or empty if embeddings are off or the call failed. */
    Optional<List<float[]>> embedAll(List<String> texts) {
        EmbeddingModel m = model.getIfAvailable();
        if (m == null || texts.isEmpty()) {
            return Optional.empty();
        }
        try {
            List<float[]> vectors = m.embed(texts);
            return vectors.size() == texts.size() ? Optional.of(vectors) : Optional.empty();
        } catch (RuntimeException e) {
            log.warn("Embedding failed ({}); chunks stored without vectors", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
