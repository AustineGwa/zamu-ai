package ai.zamu.knowledge;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Keyword tokens for search, English and Swahili. Drops common words so "how much is a filling" matches on
 * "filling", and keeps numbers so prices and times are found.
 */
public final class Tokens {

    private static final Set<String> STOP = Set.of(
            // English
            "a", "an", "the", "is", "are", "am", "be", "do", "does", "did", "you", "your", "i", "me", "my", "we",
            "our", "us", "it", "its", "of", "to", "in", "on", "at", "for", "and", "or", "with", "can", "could",
            "will", "would", "what", "how", "much", "many", "when", "where", "which", "who", "there", "this",
            "that", "please", "hi", "hello", "have", "has", "any", "get", "about", "from", "if", "so", "much",
            // Swahili
            "ni", "na", "ya", "za", "wa", "la", "kwa", "je", "gani", "ngapi", "nini", "lini", "wapi", "hii",
            "hiyo", "huu", "kuna", "mimi", "wewe", "sisi", "nyinyi", "yako", "yangu", "habari", "tafadhali",
            "naomba", "nataka", "ningependa", "mna", "una", "unaweza", "naweza", "pia", "au", "kama", "katika");

    private Tokens() {
    }

    public static Set<String> of(String text) {
        Set<String> out = new LinkedHashSet<>();
        if (text == null) {
            return out;
        }
        String plain = Normalizer.normalize(text, Normalizer.Form.NFKD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("(?<=\\d),(?=\\d{3})", "");
        for (String word : plain.split("[^\\p{L}\\p{N}]+")) {
            if (word.isEmpty() || STOP.contains(word)) {
                continue;
            }
            if (word.chars().allMatch(Character::isDigit)) {
                out.add(word);
            } else if (word.length() >= 3) {
                out.add(stem(word));
            }
        }
        return out;
    }

    /** Very light English plural stripping: "fillings" and "filling" match. */
    private static String stem(String word) {
        if (word.length() > 4 && word.endsWith("ies")) {
            return word.substring(0, word.length() - 3) + "y";
        }
        if (word.length() > 3 && word.endsWith("s") && !word.endsWith("ss")) {
            return word.substring(0, word.length() - 1);
        }
        return word;
    }
}
