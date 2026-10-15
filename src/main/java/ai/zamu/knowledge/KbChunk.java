package ai.zamu.knowledge;

import java.util.UUID;

/** A kb_chunks row: searchable text from an approved entry, with its embedding when one was computed. */
record KbChunk(UUID id, UUID entryId, String title, String language, String content, float[] embedding,
        String embeddingModel) {
}
