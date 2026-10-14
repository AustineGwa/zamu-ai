package ai.zamu.users;

import ai.zamu.shared.web.NotFoundException;

public class UserNotFoundException extends NotFoundException {

    public UserNotFoundException() {
        super("User not found");
    }
}
