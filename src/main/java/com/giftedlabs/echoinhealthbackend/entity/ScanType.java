package com.giftedlabs.echoinhealthbackend.entity;

/**
 * Types of ultrasound scans
 */
public enum ScanType {
    // Knee scans
    KNEE_JOINTS,
    LT_KNEE_JOINT,
    RT_KNEE_JOINT,

    // Elbow scans
    ELBOW_JOINTS,
    LT_ELBOW_JOINTS,
    RT_ELBOW_JOINTS,

    // Shoulder scans
    BOTH_SHOULDER_JOINT,
    RT_SHOULDER_JOINT,
    LT_SHOULDER_JOINTS,
    RT_SHOULDER_JOINTS,

    // Wrist scans
    BOTH_WRIST_JOINT,
    LT_WRIST_JOINT,
    RT_WRIST_JOINT,

    // General categories
    ABDOMEN,
    PELVIS,
    OBSTETRIC,
    GYNECOLOGICAL,
    CARDIAC,
    VASCULAR,
    MUSCULOSKELETAL,
    THYROID,
    BREAST,
    RENAL,
    HEPATOBILIARY,
    OTHER
}
