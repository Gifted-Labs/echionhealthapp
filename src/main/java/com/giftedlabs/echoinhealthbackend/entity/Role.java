package com.giftedlabs.echoinhealthbackend.entity;

/**
 * User roles in the Echoin Health system
 */
public enum Role {
    HOSPITAL_ADMIN, // Tenant administrator for a hospital or clinic
    SONOGRAPHER, // Healthcare professional who performs ultrasound scans
    RADIOLOGIST, // Reviewing clinician for reports
    PHYSICIAN, // Physician specialist role used in reporting workflows
    ADMIN, // System administrator with elevated privileges
    SUPER_ADMIN // Super administrator with full system access
}
