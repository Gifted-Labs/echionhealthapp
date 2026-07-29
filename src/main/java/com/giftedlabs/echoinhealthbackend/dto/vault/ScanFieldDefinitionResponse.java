package com.giftedlabs.echoinhealthbackend.dto.vault;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanFieldDefinitionResponse {
    private String key;
    private String label;
    private String fieldType;
    private Boolean required;
    private Boolean allowsMeasurements;
    private List<String> measurementLabels;
}
