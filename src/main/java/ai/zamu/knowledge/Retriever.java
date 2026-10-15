package ai.zamu.knowledge;

import java.util.List;
import java.util.UUID;

import ai.zamu.shared.ids.TenantId;

/**
 * Finds the approved knowledge for a customer's question (spec: Knowledge base search on MySQL). Version 1 searches
 * in memory; a vector store can replace it later without changing callers.
 */
public interface Retriever {

    /** Up to {@code limit} relevant chunks, best first; empty when nothing is relevant (AI-03). */
    List<RetrievedChunk> retrieve(TenantId tenantId, String question, int limit);

    /** A chunk with its relevance between 0 and 1. */
    record RetrievedChunk(UUID entryId, String title, String language, String content, double score) {
    }
}
