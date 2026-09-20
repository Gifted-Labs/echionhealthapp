package com.giftedlabs.echoinhealthbackend.entity;

/**
 * Lifecycle of a single outbound message in the email outbox.
 */
public enum EmailDeliveryStatus {

    /** Accepted for delivery, not yet attempted. */
    PENDING,

    /** The provider accepted the message. */
    SENT,

    /** The last attempt failed but more attempts remain. */
    RETRYING,

    /** Every permitted attempt failed. Requires operator attention. */
    FAILED
}
