package com.giftedlabs.echoinhealthbackend.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Audit log response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogResponse {
    private String id;
    private String userId;
    private String userEmail;
    private String action;
    private String details;
    private String ipAddress;
    private String userAgent;
    private Boolean success;
    private String errorMessage;
    private LocalDateTime createdAt;
}
