package ai.zamu.users;

public class StaffLimitReachedException extends RuntimeException {

    public StaffLimitReachedException(int limit) {
        super("Your plan includes " + limit + " logins. Disable a user or ask the Zamu team about upgrading.");
    }
}
