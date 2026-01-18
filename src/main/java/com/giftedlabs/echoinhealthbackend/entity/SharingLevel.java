package com.giftedlabs.echoinhealthbackend.entity;

/**
 * Sharing level for shared scans
 */
public enum SharingLevel {
    SPECIFIC_COLLEAGUES, // Shared with specific selected users
    DEPARTMENT_WIDE, // Shared with all users in the same department
    FACILITY_WIDE // Shared with all users in the same hospital/facility
}
