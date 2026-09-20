package com.giftedlabs.echoinhealthbackend.dto.platform;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Operational state of the moving parts a super admin is accountable for.
 *
 * <p>Each component reports up, degraded or down with a human-readable detail line, so the console
 * can show what is wrong rather than only that something is.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformHealthResponse {

    /** UP when every component is up, DEGRADED when any is degraded, DOWN when any is down. */
    private String status;

    private List<ComponentHealth> components;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComponentHealth {
        private String name;
        /** UP, DEGRADED or DOWN. */
        private String status;
        private String detail;
    }
}
