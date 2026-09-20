package com.giftedlabs.echoinhealthbackend.entity;

/**
 * Lifecycle state of a tenant.
 *
 * <p>A hospital previously had no off switch: once created it existed forever and its users could
 * always sign in. Suspension is reversible and deliberately does not delete anything — clinical
 * records outlive a billing dispute.
 */
public enum OrganizationStatus {

    /** Normal operation. */
    ACTIVE,

    /** Sign-in blocked for every member; data retained untouched. */
    SUSPENDED
}
