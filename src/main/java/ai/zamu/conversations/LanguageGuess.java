package ai.zamu.conversations;

import java.util.Arrays;
import java.util.Set;

/**
 * A small word-list guess at English versus Swahili until the AI step detects language (MSG-07, Milestone 3).
 * ASSUMPTION: two or more common Swahili words, or one in a message of up to three words, means Swahili.
 */
final class LanguageGuess {

    private static final Set<String> SWAHILI = Set.of("habari", "mambo", "jambo", "hujambo", "karibu", "asante",
            "sana", "naomba", "nataka", "ningependa", "tafadhali", "ni", "na", "ya", "za", "wa", "kwa", "gani",
            "ngapi", "saa", "leo", "kesho", "jana", "bei", "pesa", "mtu", "daktari", "miadi", "je", "nini", "wapi",
            "lini", "ndiyo", "hapana", "sawa", "mnafungua", "mnafunga", "unaweza", "naweza", "kuna", "hii", "hiyo",
            "yangu", "yako", "nina", "sina", "kupata", "kuona", "huduma", "shilingi", "asubuhi", "mchana", "jioni",
            "usiku", "salama", "poa", "vipi", "niaje", "sasa");

    private LanguageGuess() {
    }

    /** "sw", "en", or null when there's nothing to go on (e.g. media with no caption). */
    static String guess(String text) {
        String n = Keywords.normalise(text);
        if (n.isEmpty()) {
            return null;
        }
        String[] words = n.split(" ");
        long hits = Arrays.stream(words).filter(SWAHILI::contains).count();
        if (hits >= 2 || (hits == 1 && words.length <= 3)) {
            return "sw";
        }
        return "en";
    }
}
