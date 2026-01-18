package com.giftedlabs.echoinhealthbackend.dto.collaboration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for scan comment/feedback
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanCommentResponse {
    private String id;
    private String sharedScanId;

    // Author info
    private String authorId;
    private String authorName;
    private String authorEmail;

    // Comment content
    private String content;
    private String annotationData;
    private Boolean edited;

    // Threading
    private String parentId;
    private List<ScanCommentResponse> replies;
    private int replyCount;

    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
