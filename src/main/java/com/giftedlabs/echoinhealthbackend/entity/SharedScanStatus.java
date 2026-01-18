package com.giftedlabs.echoinhealthbackend.entity;

/**
 * Status of a shared scan in the collaboration workflow
 */
public enum SharedScanStatus {
    PENDING_REVIEW, // Awaiting initial review
    IN_REVIEW, // Being actively reviewed with comments
    RESOLVED // Owner has marked as resolved after receiving sufficient input
}
