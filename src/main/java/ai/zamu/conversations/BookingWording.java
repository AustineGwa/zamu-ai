package ai.zamu.conversations;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.Locale;

import ai.zamu.bookings.Booking;

/**
 * Booking messages, in English and Swahili, shared by the chat dialogue (ai) and the messages Zamu sends when
 * staff or the calendar change a booking. English follows Appendix B where it gives wording; everything else,
 * and all Swahili here, is ASSUMPTION awaiting review.
 */
public final class BookingWording {

    private static final String[] SW_MONTHS = {"Jan", "Feb", "Machi", "Apr", "Mei", "Juni", "Julai", "Ago", "Sep",
            "Okt", "Nov", "Des"};

    private BookingWording() {
    }

    private static boolean sw(String language) {
        return "sw".equals(language);
    }

    /** "Sat 18 Oct at 9:30am" or "Jumamosi 18 Okt saa 3:30 asubuhi". */
    public static String when(ZonedDateTime t, String language) {
        if (sw(language)) {
            return swahiliWeekday(t.getDayOfWeek()) + " " + t.getDayOfMonth() + " " + SW_MONTHS[t.getMonthValue() - 1]
                    + " " + StandardMessages.swahiliTime(t);
        }
        return t.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + t.getDayOfMonth() + " "
                + t.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " at " + StandardMessages.englishTime(t);
    }

    /** A list row title (24 characters at most): "Sat 18 Oct, 9:30am" or "Jumamosi 18 Okt 3:30". */
    public static String rowTitle(ZonedDateTime t, String language) {
        if (sw(language)) {
            String s = StandardMessages.swahiliTime(t);
            return swahiliWeekday(t.getDayOfWeek()) + " " + t.getDayOfMonth() + " " + SW_MONTHS[t.getMonthValue() - 1]
                    + " " + s.substring(4, s.lastIndexOf(' '));
        }
        return t.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + t.getDayOfMonth() + " "
                + t.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + ", " + StandardMessages.englishTime(t);
    }

    /** List row description: the period of the day in Swahili, plus who. */
    public static String rowDescription(ZonedDateTime t, String resourceName, String language) {
        String period = "";
        if (sw(language)) {
            String s = StandardMessages.swahiliTime(t);
            period = s.substring(s.lastIndexOf(' ') + 1);
        }
        String with = resourceName == null ? "" : (sw(language) ? "Na " : "With ") + resourceName;
        return (period + (period.isEmpty() || with.isEmpty() ? "" : " · ") + with).strip();
    }

    public static String money(BigDecimal amount) {
        return "KES " + new DecimalFormat("#,##0.##").format(amount);
    }

    /** Appendix B, booking confirmation. Sent with "Reschedule" and "Cancel" buttons. */
    public static String confirmed(Booking b, ZonedDateTime start, String language) {
        String service = b.serviceName(language);
        if (sw(language)) {
            return "Umewekewa miadi: " + service + (b.resourceName() == null ? "" : " na " + b.resourceName()) + ", "
                    + when(start, language) + ". Jibu 1 kubadilisha muda au 2 kughairi.";
        }
        return "You're booked: " + service + (b.resourceName() == null ? "" : " with " + b.resourceName()) + " on "
                + when(start, language) + ". Reply 1 to reschedule or 2 to cancel.";
    }

    /** Appendix B, reminder; also the body of the reminder template. */
    public static String reminder(String businessName, ZonedDateTime start, boolean tomorrow, String language) {
        if (sw(language)) {
            return "Kumbusho: miadi yako kwa " + businessName + " ni " + (tomorrow ? "kesho, " : "") + when(start, language)
                    + ". Jibu 1 kuthibitisha, 2 kubadilisha muda au 3 kughairi.";
        }
        return "Reminder: your appointment at " + businessName + " is " + (tomorrow ? "tomorrow, " : "")
                + when(start, language) + ". Reply 1 to confirm, 2 to reschedule or 3 to cancel.";
    }

    public static String chooseService(String language) {
        return sw(language) ? "Ungependa huduma gani? Chagua moja." : "Which service would you like? Choose one below.";
    }

    public static String chooseTime(String service, String language) {
        return sw(language) ? "Nafasi zilizo karibu za " + service + ". Chagua muda unaokufaa."
                : "Here are the nearest free times for " + service + ". Choose the one that suits you.";
    }

    public static String noTimes(String language) {
        return sw(language) ? "Samahani, sioni nafasi wazi katika wiki zijazo."
                : "Sorry, I can't see a free time in the coming weeks.";
    }

    public static String slotTaken(String language) {
        return sw(language) ? "Samahani, muda huo umechukuliwa sasa hivi. Hizi ndizo nafasi zilizobaki:"
                : "Sorry, that time was just taken. Here are the times still free:";
    }

    /** Interim, until M-Pesa requests are switched on for the business (Appendix B wording for the amount). */
    public static String depositByStaff(BigDecimal amount, String language) {
        return sw(language) ? "Amana ya " + money(amount) + " inahitajika kuthibitisha miadi yako. Timu yetu itakutumia "
                + "maelezo ya malipo." : "A deposit of " + money(amount) + " secures your booking. Our team will send "
                + "you the payment details.";
    }

    public static String noUpcoming(String language) {
        return sw(language) ? "Sioni miadi yoyote ijayo kwa nambari hii. Ungependa kuweka miadi mpya?"
                : "I can't find an upcoming booking for this number. Would you like to make a new one?";
    }

    public static String whichBooking(String language) {
        return sw(language) ? "Ni miadi ipi?" : "Which booking?";
    }

    public static String pastCutoff(String language) {
        return sw(language) ? "Miadi hii iko karibu sana kubadilishwa hapa."
                : "This booking is too soon to change here.";
    }

    public static String confirmCancel(Booking b, ZonedDateTime start, String language) {
        return sw(language) ? "Ughairi " + b.serviceName(language) + " ya " + when(start, language) + "?"
                : "Cancel your " + b.serviceName(language) + " on " + when(start, language) + "?";
    }

    public static String cancelledByCustomer(String language) {
        return sw(language) ? "Miadi yako imeghairiwa. Karibu tena wakati wowote."
                : "Your booking is cancelled. You're welcome to book again any time.";
    }

    public static String kept(String language) {
        return sw(language) ? "Sawa, miadi yako bado ipo." : "No problem, your booking stays as it is.";
    }

    public static String attendanceConfirmed(String language) {
        return sw(language) ? "Asante, tutakuona!" : "Thank you, see you then!";
    }

    public static String cancelledByBusiness(Booking b, ZonedDateTime start, String language) {
        return sw(language) ? "Samahani, miadi yako ya " + b.serviceName(language) + " " + when(start, language)
                + " imeghairiwa. Jibu hapa kuweka muda mwingine."
                : "Sorry, your " + b.serviceName(language) + " on " + when(start, language)
                + " has been cancelled. Reply here to book another time.";
    }

    public static String unpaidReleased(Booking b, ZonedDateTime start, String language) {
        return sw(language) ? "Nafasi ya " + b.serviceName(language) + " " + when(start, language)
                + " imeachiliwa kwa sababu amana haikupokelewa. Jibu hapa kuweka tena."
                : "The " + b.serviceName(language) + " slot on " + when(start, language)
                + " was released because the deposit didn't arrive. Reply here to book again.";
    }

    public static String moved(Booking b, ZonedDateTime start, String language) {
        return sw(language) ? "Miadi yako ya " + b.serviceName(language) + " imehamishiwa " + when(start, language)
                + ". Jibu 1 kubadilisha muda au 2 kughairi."
                : "Your " + b.serviceName(language) + " has been moved to " + when(start, language)
                + ". Reply 1 to reschedule or 2 to cancel.";
    }

    public static String rescheduleLabel(String language) {
        return sw(language) ? "Badilisha muda" : "Reschedule";
    }

    public static String cancelLabel(String language) {
        return sw(language) ? "Ghairi" : "Cancel";
    }

    public static String keepLabel(String language) {
        return sw(language) ? "Usighairi" : "Keep it";
    }

    public static String chooseTimeButton(String language) {
        return sw(language) ? "Chagua muda" : "Choose a time";
    }

    public static String chooseServiceButton(String language) {
        return sw(language) ? "Chagua huduma" : "Choose a service";
    }

    private static String swahiliWeekday(DayOfWeek d) {
        return switch (d) {
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
