package ai.zamu.conversations;

import ai.zamu.shared.web.NotFoundException;

public class ConversationNotFoundException extends NotFoundException {

    public ConversationNotFoundException() {
        super("Conversation not found");
    }
}
