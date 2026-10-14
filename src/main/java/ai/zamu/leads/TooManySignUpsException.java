package ai.zamu.leads;

public class TooManySignUpsException extends RuntimeException {

    public TooManySignUpsException() {
        super("We've received several sign-ups from your connection. Please try again in an hour.");
    }
}
