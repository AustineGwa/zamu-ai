package ai.zamu.users;

public class CannotDisableSelfException extends RuntimeException {

    public CannotDisableSelfException() {
        super("You can't disable your own login");
    }
}
