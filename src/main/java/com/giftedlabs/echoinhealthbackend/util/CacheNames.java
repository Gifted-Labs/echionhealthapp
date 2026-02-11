package com.giftedlabs.echoinhealthbackend.util;

/**
 * Centralized cache name constants.
 * Using constants prevents typos and enables IDE refactoring support.
 */
public final class CacheNames {

    private CacheNames() {
        // Prevent instantiation
    }

    /** Cache for user entities (by email and id) */
    public static final String USERS = "users";

    /** Cache for user templates */
    public static final String TEMPLATES = "templates";

    /** Cache for admin dashboard statistics */
    public static final String DASHBOARD_STATS = "dashboardStats";

    /** Cache for notification unread counts */
    public static final String NOTIFICATION_COUNTS = "notificationCounts";
}
