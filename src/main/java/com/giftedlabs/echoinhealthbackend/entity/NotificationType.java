package com.giftedlabs.echoinhealthbackend.entity;

/**
 * Types of collaboration notifications
 */
public enum NotificationType {
    NEW_SHARE, // A scan has been shared with you
    NEW_COMMENT, // Someone commented on a shared scan
    COMMENT_REPLY, // Someone replied to your comment
    SCAN_RESOLVED, // A shared scan you accessed has been resolved
    MENTIONED // You were mentioned in a comment
}
