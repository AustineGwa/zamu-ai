package ai.zamu.leads;

import ai.zamu.shared.web.NotFoundException;

public class LeadNotFoundException extends NotFoundException {

    public LeadNotFoundException() {
        super("Sign-up not found");
    }
}
