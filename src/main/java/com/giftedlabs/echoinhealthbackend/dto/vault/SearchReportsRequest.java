package com.giftedlabs.echoinhealthbackend.dto.vault;

import com.giftedlabs.echoinhealthbackend.entity.ReportType;
import com.giftedlabs.echoinhealthbackend.entity.ScanType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO for searching reports with filters
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchReportsRequest {

    // Full-text search query
    private String query;

    // Filters
    private ScanType scanType;
    private ReportType reportType;
    private String patientName;
    private LocalDate scanDateFrom;
    private LocalDate scanDateTo;
    private Boolean favoritesOnly;
    private String[] tags;

    // Pagination
    @Builder.Default
    private Integer page = 0;

    @Builder.Default
    private Integer size = 20;

    @Builder.Default
    private String sortBy = "scanDate";

    @Builder.Default
    private String sortDirection = "DESC";
}
