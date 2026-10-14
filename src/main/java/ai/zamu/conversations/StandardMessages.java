package ai.zamu.conversations;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Customer-facing wording. Sources: Operations Manual Appendix B (greeting, handover, after-hours handover,
 * Swahili greeting) and spec AI-03 (the "not sure" reply). Everything else is marked ASSUMPTION and needs the
 * Zamu team's approval; every Swahili text except the greeting needs a native speaker's review.
 */
final class StandardMessages {

    private StandardMessages() {
    }

    static boolean swahili(String language) {
        return "sw".equals(language);
    }

    /**
     * MSG-10, Appendix B greeting. The capability list is cut to what this business has switched on: answers only
     * until bookings (Milestone 5) and payments (Milestone 6) are connected, so customers aren't promised what
     * Zamu can't yet do (ASSUMPTION).
     */
    static String greeting(String language, String businessName) {
        if (swahili(language)) {
            return "Habari, karibu " + businessName + ". Mimi ni msaidizi wa kiotomatiki. Naweza kujibu maswali. "
                    + "Andika \"mtu\" wakati wowote kuongea na timu yetu.";
        }
        return "Hello, welcome to " + businessName + ". I'm the automated assistant and I can answer questions. "
                + "Type \"person\" at any time to talk to our team.";
    }

    /** HO-02, Appendix B handover. */
    static String handover(String language) {
        return swahili(language)
                ? "Ninakuunganisha na timu yetu sasa. Mtu atakujibu hivi punde."
                : "I'm connecting you to our team now. Someone will reply shortly.";
    }

    /** HO-02, Appendix B after-hours handover: "will reply by [time] on [day]". */
    static String afterHours(String language, ZonedDateTime back, LocalDate today) {
        if (back == null) {
            return swahili(language)
                    ? "Timu yetu haipo kwa sasa na itakujibu itakaporudi. Acha ujumbe wako hapa na tutakujibu."
                    : "Our team is away right now and will reply as soon as they're back. Leave your message here and we'll get back to you.";
        }
        if (swahili(language)) {
            return "Timu yetu haipo kwa sasa na itakujibu kufikia " + swahiliTime(back) + " " + swahiliDay(back, today)
                    + ". Acha ujumbe wako hapa na tutakujibu.";
        }
        return "Our team is away right now and will reply by " + englishTime(back) + " " + englishDay(back, today)
                + ". Leave your message here and we'll get back to you.";
    }

    /** AI-03 wording. */
    static String notSure(String language) {
        return swahili(language)
                ? "Sina uhakika kuhusu hilo, wacha nikuunganishe na timu yetu."
                : "I'm not sure about that, let me connect you to our team.";
    }

    /** MSG-13. ASSUMPTION: wording not in Appendix B. */
    static String nonText(String language) {
        return swahili(language)
                ? "Asante! Kwa sasa naweza kusoma ujumbe wa maandishi pekee. Andika swali lako, au andika \"mtu\" kuongea na timu yetu."
                : "Thanks! I can only read text messages for now. Type your question, or type \"person\" to talk to our team.";
    }

    /** MSG-19. ASSUMPTION: wording not in Appendix B. */
    static String optedOut(String language) {
        return swahili(language)
                ? "Sawa. Hutapokea tena vikumbusho wala matangazo kutoka kwetu. Unaweza kututumia ujumbe wakati wowote."
                : "Done. You won't get reminders or offers from us any more. You can still message us any time.";
    }

    /** MSG-09 default holding reply when the business hasn't set one. ASSUMPTION: wording not in Appendix B. */
    static String holding(String language) {
        return swahili(language)
                ? "Asante kwa ujumbe wako. Timu yetu itakujibu hapa hivi punde."
                : "Thanks for your message. Our team will reply here shortly.";
    }

    static String englishTime(ZonedDateTime t) {
        int hour = t.getHour() % 12 == 0 ? 12 : t.getHour() % 12;
        return hour + ":" + String.format("%02d", t.getMinute()) + (t.getHour() < 12 ? "am" : "pm");
    }

    static String englishDay(ZonedDateTime t, LocalDate today) {
        LocalDate date = t.toLocalDate();
        if (date.equals(today)) {
            return "today";
        }
        if (date.equals(today.plusDays(1))) {
            return "tomorrow";
        }
        return "on " + t.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }

    /** Swahili time-telling counts hours from 6am: 8:00am is "saa 2:00 asubuhi". */
    static String swahiliTime(ZonedDateTime t) {
        int h = t.getHour();
        int swahiliHour = (h + 6) % 12 == 0 ? 12 : (h + 6) % 12;
        String period = h < 4 ? "usiku" : h < 7 ? "alfajiri" : h < 12 ? "asubuhi" : h < 16 ? "mchana" : h < 19 ? "jioni" : "usiku";
        return "saa " + swahiliHour + ":" + String.format("%02d", t.getMinute()) + " " + period;
    }

    static String swahiliDay(ZonedDateTime t, LocalDate today) {
        LocalDate date = t.toLocalDate();
        if (date.equals(today)) {
            return "leo";
        }
        if (date.equals(today.plusDays(1))) {
            return "kesho";
        }
        return switch (t.getDayOfWeek()) {
            case MONDAY -> "Jumatatu";
            case TUESDAY -> "Jumanne";
            case WEDNESDAY -> "Jumatano";
            case THURSDAY -> "Alhamisi";
            case FRIDAY -> "Ijumaa";
            case SATURDAY -> "Jumamosi";
            case SUNDAY -> "Jumapili";
        };
    }
}
