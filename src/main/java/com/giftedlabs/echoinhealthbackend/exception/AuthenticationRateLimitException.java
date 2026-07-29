package com.giftedlabs.echoinhealthbackend.exception;

public class AuthenticationRateLimitException extends RuntimeException {
    public AuthenticationRateLimitException(String message) {
        super(message);
    }
}
