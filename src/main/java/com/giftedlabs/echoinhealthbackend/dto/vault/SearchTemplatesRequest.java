package com.giftedlabs.echoinhealthbackend.dto.vault;

import com.giftedlabs.echoinhealthbackend.entity.ReportType;
import com.giftedlabs.echoinhealthbackend.entity.ScanType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchTemplatesRequest {
    private String keyword;
    private ScanType scanType;
    private ReportType reportType;
    private String category;
    private String tag;
    private Boolean favoritesOnly;
    private LocalDateTime createdAfter;
    private LocalDateTime createdBefore;
    private String sortBy;
    private String sortDirection;
}
