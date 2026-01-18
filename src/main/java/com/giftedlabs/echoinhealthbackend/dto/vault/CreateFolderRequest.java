package com.giftedlabs.echoinhealthbackend.dto.vault;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for creating a folder
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateFolderRequest {

    @NotBlank(message = "Folder name is required")
    private String name;

    private String description;

    private String parentFolderId; // null for root folder
}
