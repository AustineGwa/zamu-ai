package ai.zamu.conversations;

import java.util.List;
import java.util.Objects;

/**
 * Options a customer taps instead of typing: a WhatsApp list (up to 10 rows) or reply buttons (up to 3). The
 * message body always lists the options too, so the text reads correctly on any channel.
 *
 * @param button the list's menu button label, e.g. "Choose a time"; unused for buttons
 * @param asButtons true for reply buttons, false for a list
 */
public record Interactive(String button, List<Choice> choices, boolean asButtons) {

    /** WhatsApp Cloud API limits. ASSUMPTION: as documented by Meta at the time of writing. */
    public static final int MAX_ROWS = 10;
    public static final int MAX_BUTTONS = 3;
    public static final int ROW_TITLE_MAX = 24;
    public static final int BUTTON_TITLE_MAX = 20;
    public static final int DESCRIPTION_MAX = 72;
    public static final int MENU_BUTTON_MAX = 20;

    /**
     * @param id    returned in the customer's reply; at most 200 characters
     * @param title shown on the row or button
     * @param description a second line on list rows, or null
     */
    public record Choice(String id, String title, String description) {

        public Choice {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(title, "title");
        }
    }

    public Interactive {
        choices = List.copyOf(choices);
        if (choices.isEmpty() || choices.size() > (asButtons ? MAX_BUTTONS : MAX_ROWS)) {
            throw new IllegalArgumentException("Wrong number of options: " + choices.size());
        }
    }

    public static Interactive list(String button, List<Choice> choices) {
        return new Interactive(cut(button, MENU_BUTTON_MAX), choices.stream()
                .map(c -> new Choice(c.id(), cut(c.title(), ROW_TITLE_MAX), cut(c.description(), DESCRIPTION_MAX)))
                .toList(), false);
    }

    public static Interactive buttons(List<Choice> choices) {
        return new Interactive(null, choices.stream()
                .map(c -> new Choice(c.id(), cut(c.title(), BUTTON_TITLE_MAX), null)).toList(), true);
    }

    static String cut(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max - 1).stripTrailing() + "…";
    }
}
