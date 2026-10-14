package ai.zamu.conversations;

public class TemplateNotAvailableException extends RuntimeException {

    public TemplateNotAvailableException() {
        super("That template isn't approved for this business, or the values don't match its placeholders.");
    }
}
