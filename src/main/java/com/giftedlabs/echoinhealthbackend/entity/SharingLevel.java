package com.giftedlabs.echoinhealthbackend.entity;

/**
 * Sharing level for shared scans in SonoShare collaboration
 */
public enum SharingLevel {
    /**
     * Shared with specific selected users only
     */
    SPECIFIC_COLLEAGUES,

    /**
     * Shared with all users in a specific department within the organization (UR-060)
     */
    DEPARTMENT,

    /**
     * Shared with everyone in the organization
     */
    EVERYONE,

    /**
     * Explicit org-wide alias matching the v2.0 requirements language
     */
    ORGANIZATION_WIDE
}
