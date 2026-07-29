package com.giftedlabs.echoinhealthbackend.dto.vault;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateVersionResponse {
    private String id;
    private String templateId;
    private Integer versionNumber;
    private String changeDescription;
    private String changedByName;
    private String changedByEmail;
    private LocalDateTime createdAt;
}
