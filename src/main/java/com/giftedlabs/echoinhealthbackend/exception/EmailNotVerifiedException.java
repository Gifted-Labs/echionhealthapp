package com.giftedlabs.echoinhealthbackend.exception;

/**
 * Exception thrown when attempting to login with an unverified email
 */
public class EmailNotVerifiedException extends RuntimeException {
    public EmailNotVerifiedException(String message) {
        super(message);
    }
}
