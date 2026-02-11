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
     * Shared with everyone on the entire system
     */
    EVERYONE
}
