package ai.zamu.knowledge;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.ids.UuidV7;
import ai.zamu.shared.jobs.Job;
import ai.zamu.shared.jobs.JobHandler;

import tools.jackson.databind.ObjectMapper;

/**
 * Job {@code reembed_kb_entry} (spec: Knowledge base search, step 1; AI-12). Rebuilds an entry's chunks from its
 * current text, with embeddings when a provider is configured. Embedding calls happen outside the database
 * transaction; the swap of old for new chunks is one transaction. Idempotent.
 */
@Component
class ReembedHandler implements JobHandler {

    private final KbEntryRepository entries;
    private final KbChunkRepository chunks;
    private final InMemoryRetriever retriever;
    private final Embeddings embeddings;
    private final TransactionTemplate tx;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    ReembedHandler(KbEntryRepository entries, KbChunkRepository chunks, InMemoryRetriever retriever,
            Embeddings embeddings, TransactionTemplate tx, ObjectMapper objectMapper, Clock clock) {
        this.entries = entries;
        this.chunks = chunks;
        this.retriever = retriever;
        this.embeddings = embeddings;
        this.tx = tx;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public String type() {
        return KnowledgeService.REEMBED_JOB;
    }

    @Override
    public void handle(Job job) {
        TenantId tenantId = job.tenantId();
        UUID entryId = UUID.fromString(objectMapper.readTree(job.payloadJson()).path("entryId").asString(""));
        KbEntry entry = entries.findById(tenantId, entryId).orElse(null);
        if (entry == null || entry.status() != KbEntryStatus.APPROVED) {
            tx.executeWithoutResult(s -> chunks.deleteForEntry(tenantId, entryId));
            retriever.evict(tenantId);
            return;
        }
        List<Chunker.ChunkText> texts = Chunker.chunks(entry);
        List<float[]> vectors = embeddings.embedAll(texts.stream().map(Chunker.ChunkText::content).toList())
                .orElse(null);
        String model = vectors == null ? null : embeddings.label();
        tx.executeWithoutResult(s -> {
            chunks.deleteForEntry(tenantId, entryId);
            for (int i = 0; i < texts.size(); i++) {
                chunks.insert(tenantId, UuidV7.next(clock), entryId, texts.get(i).language(), texts.get(i).content(),
                        vectors == null ? null : vectors.get(i), model, clock.instant());
            }
        });
        retriever.evict(tenantId);
    }
}
