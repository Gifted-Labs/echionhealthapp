package com.giftedlabs.echoinhealthbackend.dto.collaboration;

import com.giftedlabs.echoinhealthbackend.entity.SharingLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for sharing a scan for collaboration
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShareScanRequest {

    @NotBlank(message = "Report ID is required")
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
