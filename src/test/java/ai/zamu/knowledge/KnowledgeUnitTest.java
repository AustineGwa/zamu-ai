package ai.zamu.knowledge;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeUnitTest {

    @Test
    void tokensDropCommonWordsKeepNumbersAndMatchPlurals() {
        assertThat(Tokens.of("How much is a filling?")).containsExactly("filling");
        assertThat(Tokens.of("Fillings cost KES 4,500")).contains("filling", "cost", "ke", "4500");
        assertThat(Tokens.of("Mnafungua saa ngapi Jumamosi?")).containsExactly("mnafungua", "saa", "jumamosi");
        assertThat(Tokens.of("Hii ni nini?")).isEmpty();
    }

    @Test
    void AI_12_eachApprovedEntryBecomesOneChunkPerLanguage() {
        KbEntry entry = new KbEntry(UUID.randomUUID(), UUID.randomUUID(), "Fillings", "How much is a filling?",
                "KES 4,500 to 8,000.", "KES 4,500 hadi 8,000.", KbEntryStatus.APPROVED, null, null, null,
                Instant.now(), Instant.now());
        List<Chunker.ChunkText> chunks = Chunker.chunks(entry);
        assertThat(chunks).extracting(Chunker.ChunkText::language).containsExactly("en", "sw");
        assertThat(chunks.getFirst().content()).isEqualTo("Fillings\nHow much is a filling?\nKES 4,500 to 8,000.");
    }

    @Test
    void longAnswersAreSplitByParagraphAndKeepTheirTitle() {
        String paragraph = "word ".repeat(150).strip();
        KbEntry entry = new KbEntry(UUID.randomUUID(), UUID.randomUUID(), "Policies", "What are your policies?",
                paragraph + "\n\n" + paragraph + "\n\n" + paragraph, null, KbEntryStatus.APPROVED, null, null, null,
                Instant.now(), Instant.now());
        List<Chunker.ChunkText> chunks = Chunker.chunks(entry);
        assertThat(chunks).hasSizeGreaterThan(1)
                .allSatisfy(c -> assertThat(c.content()).startsWith("Policies\nWhat are your policies?\n"));
    }

    @Test
    void embeddingsRoundTripAsFloat32AndCompareByCosine() {
        float[] v = {0.5f, -1.25f, 3f};
        assertThat(EmbeddingCodec.decode(EmbeddingCodec.encode(v))).containsExactly(v);
        assertThat(EmbeddingCodec.encode(v)).hasSize(12);
        assertThat(EmbeddingCodec.cosine(v, v)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(EmbeddingCodec.cosine(v, new float[] {1f})).isZero();
        assertThat(EmbeddingCodec.decode(null)).isNull();
    }
}
