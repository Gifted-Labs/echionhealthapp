package com.giftedlabs.echoinhealthbackend.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void malformedMultipartRequestReturnsBadRequestInsteadOfServerError() {
        var response = handler.handleMultipart(
                new MultipartException("no multipart boundary was found"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("FormData", "boundary");
    }

    @Test
    void oversizedMultipartRequestReturnsPayloadTooLarge() {
        var response = handler.handleMaxUploadSizeExceeded(
                new MaxUploadSizeExceededException(10));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("maximum allowed size");
    }
}
