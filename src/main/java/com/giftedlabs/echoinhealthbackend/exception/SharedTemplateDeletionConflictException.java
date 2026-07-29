package com.giftedlabs.echoinhealthbackend.exception;

/**
 * Raised when an owner tries to delete a template that is still shared with
 * colleagues without explicitly confirming cascading share removal.
 */
public class SharedTemplateDeletionConflictException extends BusinessException {

    public SharedTemplateDeletionConflictException(String templateName, long sharedCount) {
        super("Template '" + templateName + "' is currently shared with " + sharedCount
                + " colleague(s). Retry with cascadeSharedAccess=true to remove those shares and delete it.");
    }
}
