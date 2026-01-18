package com.giftedlabs.echoinhealthbackend.dto.vault;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for folder response with report count
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FolderResponse {

    private String id;
    private String userId;

    private String name;
    private String description;

    private String parentFolderId;
    private String parentFolderName;

    private Integer reportCount;

    private LocalDateTime createdAt;
}
