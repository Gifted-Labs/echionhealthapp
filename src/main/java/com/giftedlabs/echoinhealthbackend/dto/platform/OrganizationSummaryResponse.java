package com.giftedlabs.echoinhealthbackend.dto.platform;

import com.giftedlabs.echoinhealthbackend.entity.OrganizationStatus;
import com.giftedlabs.echoinhealthbackend.entity.SubscriptionTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One tenant as it appears in the platform console's organization list.
 *
 * <p>This list is what the client asked for when they reported that a newly onboarded hospital
 * never appeared anywhere: no endpoint returned organizations at all.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationSummaryResponse {

    private String id;
    private String name;
    private String hospitalName;
    private String email;
    private String phone;
    private String address;
    private String website;

    private SubscriptionTier subscriptionTier;
    private OrganizationStatus status;

    private long userCount;
    private long activeUserCount;
    private int seatLimit;

    private long reportCount;
    private long storageUsedMb;
    private int storageLimitMb;

    private boolean hasLetterhead;

    private LocalDateTime createdAt;
    private LocalDateTime suspendedAt;
    private String suspensionReason;
}
