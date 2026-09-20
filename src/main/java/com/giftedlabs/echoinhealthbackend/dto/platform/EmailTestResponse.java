package com.giftedlabs.echoinhealthbackend.dto.platform;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * What the email provider actually said.
 *
 * <p>Returned verbatim rather than reduced to a boolean: the reason onboarding mail was silently
 * failing was that nothing ever surfaced the provider's answer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailTestResponse {

    private boolean delivered;
    private String recipient;
    private String sentFrom;
    private Integer providerStatusCode;
    private String providerMessageId;
    private String error;

    /** Outbox row for this attempt, so the operator can follow it up. */
    private String outboxEntryId;
}
