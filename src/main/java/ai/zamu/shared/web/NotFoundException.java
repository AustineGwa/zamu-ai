package ai.zamu.shared.web;

/**
 * Base for "not found in this tenant" exceptions. A record that exists in another tenant is reported as not
 * found, never as forbidden, so IDs from other tenants reveal nothing (NFR-09).
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
