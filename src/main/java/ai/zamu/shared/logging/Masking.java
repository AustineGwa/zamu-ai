package ai.zamu.shared.logging;

/**
 * Masks personal data before it reaches a log line (NFR-13). Never log raw phone numbers, emails, names or
 * message bodies; when an identifier is needed for support, log a masked one.
 */
public final class Masking {

    private Masking() {
    }

    /** "254712345123" becomes "2547XXXXX123" (spec NFR-13 example). */
    public static String phone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "";
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() <= 7) {
            return "X".repeat(digits.length());
        }
        return digits.substring(0, 4) + "X".repeat(digits.length() - 7) + digits.substring(digits.length() - 3);
    }

    /** "jane.doe@clinic.co.ke" becomes "j***@clinic.co.ke". */
    public static String email(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
