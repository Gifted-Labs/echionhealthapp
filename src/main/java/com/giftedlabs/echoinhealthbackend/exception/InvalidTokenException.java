package com.giftedlabs.echoinhealthbackend.exception;

/**
 * Exception thrown when a token (verification or refresh) is invalid or expired
 */
public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException(String message) {
        super(message);
    }
}
