package ai.zamu.users;

public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException() {
        super("Someone already uses this email address for Zamu");
    }
}
