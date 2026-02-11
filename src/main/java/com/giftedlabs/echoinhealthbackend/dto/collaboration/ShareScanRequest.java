package com.giftedlabs.echoinhealthbackend.dto.collaboration;

import com.giftedlabs.echoinhealthbackend.entity.SharingLevel;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for sharing a scan or image for collaboration.
 * Can share either a report OR an image (at least one is required).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShareScanRequest {

    /**
     * Report ID to share (optional if sharing an image)
     */
    private String reportId;

    @NotNull(message = "Sharing level is required")
    private SharingLevel sharingLevel;

    /**
     * Required when sharingLevel is SPECIFIC_COLLEAGUES
     */
    private List<String> colleagueIds;

    /**
     * Title/subject for the collaboration request
     */
    private String title;

    /**
     * Message explaining what feedback is needed
     */
    private String requestMessage;
}
