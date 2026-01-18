package com.giftedlabs.echoinhealthbackend.dto.collaboration;

import com.giftedlabs.echoinhealthbackend.entity.SharedScanStatus;
import com.giftedlabs.echoinhealthbackend.entity.SharingLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for shared scan details
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SharedScanResponse {
    private String id;
    private String reportId;
    private String reportPatientName;
    private String reportScanType;
    private LocalDateTime reportScanDate;

    // Owner info
    private String ownerId;
    private String ownerName;
    private String ownerEmail;
    private String ownerDepartment;
    private String ownerHospital;

    // Sharing details
    private SharingLevel sharingLevel;
    private SharedScanStatus status;
    private String title;
    private String requestMessage;

    // Statistics
    private long commentCount;
    private long accessCount;

    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
    private String resolutionNotes;
}
