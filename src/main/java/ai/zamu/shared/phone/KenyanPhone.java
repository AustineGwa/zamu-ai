package ai.zamu.shared.phone;

import java.util.Optional;

/**
 * Kenyan mobile numbers in the international format M-Pesa and WhatsApp use: 2547XXXXXXXX or 2541XXXXXXXX
 * (spec PAY-04). Accepts what people type: 0712 345 678, 712345678, +254 712 345 678, 254-712-345-678.
 */
public final class KenyanPhone {

    private KenyanPhone() {
    }

    public static Optional<String> normalise(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String digits = input.replaceAll("[\\s\\-().]", "");
        if (digits.startsWith("+")) {
            digits = digits.substring(1);
        }
        if (!digits.matches("\\d+")) {
            return Optional.empty();
        }
        if (digits.startsWith("254")) {
            digits = digits.substring(3);
        } else if (digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        return digits.matches("[17]\\d{8}") ? Optional.of("254" + digits) : Optional.empty();
    }

    /** 254712345678 shown as 0712 345 678. */
    public static String display(String normalised) {
        if (normalised == null || !normalised.matches("254\\d{9}")) {
            return normalised == null ? "" : normalised;
        }
        String local = "0" + normalised.substring(3);
        return local.substring(0, 4) + " " + local.substring(4, 7) + " " + local.substring(7);
    }
}
