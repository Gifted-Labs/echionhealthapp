package com.giftedlabs.echoinhealthbackend.dto.vault;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoSaveReportRequest {
    private String clinicalHistory;
    private String findings;
    private String impression;
    private String recommendation;
    private Map<String, Object> structuredFindings;
    private String[] recommendationOptions;
}
