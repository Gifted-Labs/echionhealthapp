package com.giftedlabs.echoinhealthbackend.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Request DTO for searching/filtering audit logs
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogSearchRequest {
    private String action; // Filter by action type
    private String userEmail; // Filter by user email
    private Boolean success; // Filter by success/failure
    private LocalDateTime startDate;
    private LocalDateTime endDate;
}
