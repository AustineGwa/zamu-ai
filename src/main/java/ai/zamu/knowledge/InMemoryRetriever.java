package ai.zamu.knowledge;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import ai.zamu.shared.ids.TenantId;

/**
 * Spec: Knowledge base search on MySQL. Each tenant's approved chunks are cached in memory (refilled when an entry
 * changes); a question is scored by keyword overlap (IDF-weighted) and, when an embedding model is configured, by
 * cosine similarity. The better of the two scores counts, so exact names and prices are always found.
 *
 * <p>ASSUMPTION: relevance thresholds below are starting values; tune them with the evaluation set.
 */
@Component
class InMemoryRetriever implements Retriever {

    /** Below this keyword score a chunk isn't relevant. */
    static final double MIN_KEYWORD = 0.34;
    /** Below this cosine similarity a chunk isn't relevant. */
    static final double MIN_COSINE = 0.45;

    record Indexed(KbChunk chunk, Set<String> tokens) {
    }

    record TenantIndex(List<Indexed> chunks, Map<String, Integer> documentFrequency) {
    }

    private final KbChunkRepository chunks;
    private final Embeddings embeddings;
    private final Cache<TenantId, TenantIndex> cache = Caffeine.newBuilder()
            .maximumSize(2_000)
            .expireAfterAccess(Duration.ofHours(6))
            .build();

    InMemoryRetriever(KbChunkRepository chunks, Embeddings embeddings) {
        this.chunks = chunks;
        this.embeddings = embeddings;
    }

    @Override
    public List<RetrievedChunk> retrieve(TenantId tenantId, String question, int limit) {
        TenantIndex index = cache.get(tenantId, this::load);
        if (index.chunks().isEmpty()) {
            return List.of();
        }
        Set<String> queryTokens = Tokens.of(question);
        float[] queryVector = index.chunks().stream().anyMatch(i -> i.chunk().embedding() != null)
                ? embeddings.embed(question).orElse(null) : null;
        String model = embeddings.label();

        int n = index.chunks().size();
        double queryWeight = 0;
        for (String t : queryTokens) {
            queryWeight += idf(n, index.documentFrequency().getOrDefault(t, 0));
        }

        List<RetrievedChunk> scored = new ArrayList<>();
        for (Indexed i : index.chunks()) {
            double keyword = 0;
            if (queryWeight > 0) {
                double matched = 0;
                for (String t : queryTokens) {
                    if (i.tokens().contains(t)) {
                        matched += idf(n, index.documentFrequency().getOrDefault(t, 0));
                    }
                }
                keyword = matched / queryWeight;
            }
            double cosine = queryVector != null && model.equals(i.chunk().embeddingModel())
                    ? EmbeddingCodec.cosine(queryVector, i.chunk().embedding()) : 0;
            boolean relevant = keyword >= MIN_KEYWORD || cosine >= MIN_COSINE;
            if (relevant) {
                double score = Math.max(keyword, cosine);
                scored.add(new RetrievedChunk(i.chunk().entryId(), i.chunk().title(), i.chunk().language(),
                        i.chunk().content(), Math.min(1.0, score)));
            }
        }
        scored.sort(Comparator.comparingDouble(RetrievedChunk::score).reversed());
        return dedupeByEntryAndLanguage(scored, limit);
    }

    /** Called when an entry is approved, changed or archived (AI-12). */
    void evict(TenantId tenantId) {
        cache.invalidate(tenantId);
    }

    private TenantIndex load(TenantId tenantId) {
        List<Indexed> list = new ArrayList<>();
        Map<String, Integer> df = new HashMap<>();
        for (KbChunk c : chunks.findApproved(tenantId)) {
            Set<String> tokens = Tokens.of(c.content());
            list.add(new Indexed(c, tokens));
            for (String t : tokens) {
                df.merge(t, 1, Integer::sum);
            }
        }
        return new TenantIndex(List.copyOf(list), Map.copyOf(df));
    }

    private static double idf(int documents, int frequency) {
        return Math.log(1.0 + (documents + 1.0) / (frequency + 0.5));
    }

    /** Keeps the best few chunks, at most two per entry, so one long entry can't crowd out the rest. */
    private static List<RetrievedChunk> dedupeByEntryAndLanguage(List<RetrievedChunk> scored, int limit) {
        Map<UUID, Integer> perEntry = new HashMap<>();
        List<RetrievedChunk> out = new ArrayList<>();
        for (RetrievedChunk c : scored) {
            if (perEntry.merge(c.entryId(), 1, Integer::sum) <= 2) {
                out.add(c);
            }
            if (out.size() == limit) {
                break;
            }
        }
        return out;
    }
}
