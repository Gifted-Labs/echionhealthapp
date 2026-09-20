package com.giftedlabs.echoinhealthbackend.dto.collaboration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single-use credential for opening the notification stream from a browser {@code EventSource},
 * which cannot send an Authorization header.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamTokenResponse {

    /** Single-use token. Valid for one minute and consumed by the first connection that uses it. */
    private String token;

    private Integer expiresInSeconds;

    /** Ready-to-use URL, so the front-end does not have to assemble the query string itself. */
    private String streamUrl;
}
