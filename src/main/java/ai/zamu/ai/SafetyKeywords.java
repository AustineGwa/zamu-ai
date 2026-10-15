package ai.zamu.ai;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * AI-04: messages that describe symptoms or an emergency get the emergency reply and an immediate handover, before
 * any model call. The model can also report intent "emergency"; this list catches the clear cases even when no
 * model is available. ASSUMPTION: starting list in English, Swahili and common Sheng; extend it from real chats.
 */
final class SafetyKeywords {

    private static final List<String> EMERGENCY = List.of(
            // English
            "emergency", "chest pain", "heart attack", "stroke", "can't breathe", "cannot breathe", "cant breathe",
            "difficulty breathing", "not breathing", "unconscious", "fainted", "collapsed", "seizure", "convulsion",
            "bleeding", "overdose", "poison", "suicide", "kill myself", "severe pain", "accident", "broken bone",
            "fever", "vomiting", "swollen", "swelling", "allergic reaction", "miscarriage", "labour pains",
            "labor pains",
            // Swahili and Sheng
            "dharura", "maumivu makali", "kifua kinauma", "maumivu ya kifua", "siwezi kupumua", "hawezi kupumua",
            "anatokwa na damu", "natokwa na damu", "damu nyingi", "amezimia", "nimezimia", "kifafa", "ajali",
            "sumu", "kujiua", "homa kali", "kutapika", "anatapika", "natapika", "uvimbe", "imevimba");

    private SafetyKeywords() {
    }

    static boolean emergency(String text) {
        String n = normalise(text);
        return EMERGENCY.stream().anyMatch(n::contains);
    }

    /** HO-01: the business's "always hand over" topics, matched as words or phrases. */
    static boolean mentionsAny(String text, List<String> topics) {
        String n = " " + normalise(text) + " ";
        for (String topic : topics) {
            String t = normalise(topic);
            if (!t.isBlank() && n.contains(" " + t + " ")) {
                return true;
            }
        }
        return false;
    }

    private static String normalise(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFKD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
                .replace('’', '\'').replaceAll("[^\\p{L}\\p{N}' ]+", " ").replaceAll("\\s+", " ").strip();
    }
}
