package com.giftedlabs.echoinhealthbackend.entity;

/**
 * Storage type for uploaded files
 */
public enum StorageType {
    LOCAL, // Local filesystem storage (development)
    R2 // Cloudflare R2 object storage (production)
}
