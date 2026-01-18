package com.giftedlabs.echoinhealthbackend.dto.collaboration;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for adding a comment to a shared scan
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddCommentRequest {

    @NotBlank(message = "Comment content is required")
    private String content;

    /**
     * JSON data for annotation positioning (optional)
     * e.g., { "x": 100, "y": 200, "width": 50, "height": 50, "type": "rectangle" }
     */
    private String annotationData;

    /**
     * Parent comment ID for replies (null for top-level comments)
     */
    private String parentId;
}
