package ai.zamu.knowledge;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns an approved entry into searchable chunks: one per language, split by paragraph when an answer is long.
 * Each chunk repeats the title and question so it stands on its own in the prompt.
 */
final class Chunker {

    static final int MAX_CHARS = 1200;

    record ChunkText(String language, String content) {
    }

    private Chunker() {
    }

    static List<ChunkText> chunks(KbEntry entry) {
        List<ChunkText> out = new ArrayList<>();
        add(out, "en", entry.title(), entry.question(), entry.answerEn());
        if (entry.answerSw() != null && !entry.answerSw().isBlank()) {
            add(out, "sw", entry.title(), entry.question(), entry.answerSw());
        }
        return out;
    }

    private static void add(List<ChunkText> out, String language, String title, String question, String answer) {
        String head = title + "\n" + question + "\n";
        String body = answer.strip();
        if (head.length() + body.length() <= MAX_CHARS) {
            out.add(new ChunkText(language, head + body));
            return;
        }
        StringBuilder current = new StringBuilder();
        for (String paragraph : body.split("\\R\\s*\\R")) {
            if (current.length() > 0 && head.length() + current.length() + paragraph.length() > MAX_CHARS) {
                out.add(new ChunkText(language, head + current.toString().strip()));
                current.setLength(0);
            }
            current.append(paragraph.strip()).append("\n\n");
        }
        if (current.length() > 0) {
            out.add(new ChunkText(language, head + current.toString().strip()));
        }
    }
}
