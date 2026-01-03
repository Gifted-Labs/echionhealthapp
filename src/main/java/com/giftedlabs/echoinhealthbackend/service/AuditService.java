package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.entity.AuditLog;
import com.giftedlabs.echoinhealthbackend.entity.User;
import com.giftedlabs.echoinhealthbackend.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Service for audit logging - critical for HIPAA compliance
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Log a successful action
     */
    @Async
    public void logAction(User user, String action, String details) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .user(user)
                    .userEmail(user.getEmail())
                    .action(action)
                    .details(details)
                    .success(true)
                    .ipAddress(getClientIpAddress())
                    .userAgent(getUserAgent())
                    .build();

            auditLogRepository.save(auditLog);
            log.info("Audit log created: {} - {} - {}", user.getEmail(), action, details);
        } catch (Exception e) {
            log.error("Failed to create audit log", e);
        }
    }

    /**
     * Log a failed action
     */
    @Async
    public void logFailedAction(String userEmail, String action, String errorMessage) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .userEmail(userEmail)
                    .action(action)
                    .success(false)
                    .errorMessage(errorMessage)
                    .ipAddress(getClientIpAddress())
                    .userAgent(getUserAgent())
                    .build();

            auditLogRepository.save(auditLog);
            log.warn("Failed action logged: {} - {} - {}", userEmail, action, errorMessage);
        } catch (Exception e) {
            log.error("Failed to create audit log for failed action", e);
        }
    }

    /**
     * Log action without user (e.g., registration attempt)
     */
    @Async
    public void logAction(String userEmail, String action, String details, boolean success) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .userEmail(userEmail)
                    .action(action)
                    .details(details)
                    .success(success)
                    .ipAddress(getClientIpAddress())
                    .userAgent(getUserAgent())
                    .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to create audit log", e);
        }
    }

    /**
     * Get client IP address from request
     */
    private String getClientIpAddress() {
        try {
            HttpServletRequest request = getCurrentRequest();
            if (request == null) {
                return "unknown";
            }

            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                return xForwardedFor.split(",")[0].trim();
            }

            String xRealIp = request.getHeader("X-Real-IP");
            if (xRealIp != null && !xRealIp.isEmpty()) {
                return xRealIp;
            }

            return request.getRemoteAddr();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Get user agent from request
     */
    private String getUserAgent() {
        try {
            HttpServletRequest request = getCurrentRequest();
            if (request == null) {
                return "unknown";
            }
            return request.getHeader("User-Agent");
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Get current HTTP request
     */
    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
}
