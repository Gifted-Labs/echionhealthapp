package com.giftedlabs.echoinhealthbackend.exception;

/**
 * Exception thrown when a user attempts to access a resource
 * they are not authorized to access.
 */
public class AccessDeniedException extends BusinessException {

    public AccessDeniedException(String message) {
        super(message);
    }

    /**
     * Factory method for creating an exception with resource context
     */
    public static AccessDeniedException forResource(String resource) {
        return new AccessDeniedException("Not authorized to access: " + resource);
    }
}
