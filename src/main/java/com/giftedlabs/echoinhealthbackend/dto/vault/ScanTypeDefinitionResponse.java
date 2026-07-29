package com.giftedlabs.echoinhealthbackend.dto.vault;

import com.giftedlabs.echoinhealthbackend.entity.ScanType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanTypeDefinitionResponse {
    private ScanType scanType;
    private String displayName;
    private String category;
    private List<ScanFieldDefinitionResponse> sections;
    private List<String> recommendationOptions;
    private List<String> measurementColumns;
    private Boolean hasMeasurementTable;
    private Boolean freeFormTemplate;
}
