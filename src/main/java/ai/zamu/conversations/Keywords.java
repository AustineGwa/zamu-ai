package ai.zamu.conversations;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/** Fixed words customers can type at any time (MSG-18, MSG-19), plus a greeting check. */
final class Keywords {

    /** MSG-18: "person", "mtu", "agent" or similar. */
    private static final Set<String> PERSON = Set.of("person", "mtu", "agent", "human", "staff", "mhudumu",
            "binadamu", "receptionist", "operator");

    /** MSG-19: "stop". ASSUMPTION: Swahili equivalents "acha" and "sitisha". */
    private static final Set<String> STOP = Set.of("stop", "unsubscribe", "acha", "sitisha");

    private static final Set<String> GREETINGS = Set.of("hi", "hello", "hey", "hallo", "habari", "mambo", "sasa",
            "niaje", "jambo", "hujambo", "salama", "good", "morning", "afternoon", "evening", "there", "yako", "za",
            "asubuhi", "mchana", "jioni", "vipi", "poa");

    private Keywords() {
    }

    static String normalise(String text) {
        if (text == null) {
            return "";
        }
        String lower = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return lower.replaceAll("[^\\p{L}\\p{N}\\s]", " ").replaceAll("\\s+", " ").trim();
    }

    private static String[] words(String text) {
        String n = normalise(text);
        return n.isEmpty() ? new String[0] : n.split(" ");
    }

    /** A short message (up to 6 words) that names a person: "person", "talk to a person", "nataka mtu". */
    static boolean asksForPerson(String text) {
        String[] w = words(text);
        return w.length > 0 && w.length <= 6 && Arrays.stream(w).anyMatch(PERSON::contains);
    }

    /** Exactly "stop" (or an equivalent), so a sentence containing the word doesn't opt anyone out. */
    static boolean isStop(String text) {
        return STOP.contains(normalise(text));
    }

    /** Only greeting words, at most four: "hi", "habari za asubuhi", "good morning". */
    static boolean isGreetingOnly(String text) {
        String[] w = words(text);
        return w.length > 0 && w.length <= 4 && Arrays.stream(w).allMatch(GREETINGS::contains);
    }
}
