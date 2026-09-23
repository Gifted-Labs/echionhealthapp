package com.giftedlabs.echoinhealthbackend.exception;

/**
 * Raised when configured object storage cannot complete an operation.
 *
 * <p>This is intentionally distinct from validation and database failures so clients receive a
 * retryable 503 response rather than an opaque 500 response.
 */
public class StorageOperationException extends RuntimeException {

    public StorageOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
