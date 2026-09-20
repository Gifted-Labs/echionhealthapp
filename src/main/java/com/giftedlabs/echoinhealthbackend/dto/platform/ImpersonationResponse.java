package com.giftedlabs.echoinhealthbackend.dto.platform;

import com.giftedlabs.echoinhealthbackend.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A live impersonation session.
 *
 * <p>No refresh token is issued, deliberately: an impersonated session must expire rather than be
 * renewable, so it cannot quietly become a permanent second identity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImpersonationResponse {

    /** Bearer token that acts as the target user. Short-lived and not renewable. */
    private String accessToken;

    @Builder.Default
    private String tokenType = "Bearer";

    private long expiresInSeconds;

    /** Pass this to the stop endpoint to end the session before it expires. */
    private String impersonationId;

    private String targetUserId;
    private String targetUserEmail;
    private String targetUserFullName;
    private Role targetUserRole;
    private String targetOrganizationId;
    private String targetOrganizationName;

    private String impersonatorEmail;
}
