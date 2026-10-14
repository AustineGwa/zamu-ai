package ai.zamu.conversations;

/** HO-07: free-form replies are only possible within 24 hours of the customer's last message. */
public class WindowClosedException extends RuntimeException {

    public WindowClosedException() {
        super("More than 24 hours have passed since the customer's last message, so WhatsApp only allows an "
                + "approved template. Choose one below.");
    }
}
