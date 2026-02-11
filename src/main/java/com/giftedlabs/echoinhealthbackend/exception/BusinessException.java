package com.giftedlabs.echoinhealthbackend.exception;

/**
 * Base exception for business logic errors.
 * Extend this class for specific business exceptions.
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
