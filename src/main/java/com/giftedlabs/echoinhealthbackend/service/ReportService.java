package com.giftedlabs.echoinhealthbackend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.giftedlabs.echoinhealthbackend.dto.vault.*;
import com.giftedlabs.echoinhealthbackend.entity.*;
import com.giftedlabs.echoinhealthbackend.exception.ResourceNotFoundException;
import com.giftedlabs.echoinhealthbackend.repository.ReportRepository;
import com.giftedlabs.echoinhealthbackend.repository.ReportVersionRepository;
import com.giftedlabs.echoinhealthbackend.repository.UserRepository;
import com.giftedlabs.echoinhealthbackend.util.ReportDocumentParser;
import com.giftedlabs.echoinhealthbackend.util.TextExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final ReportRepository reportRepository;
    private final ReportVersionRepository versionRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final TextExtractor textExtractor;
    private final ReportDocumentParser documentParser;
    private final AuditService auditService;
    private final TemplateService templateService;
    private final ObjectMapper objectMapper;

    // ========== Upload Operations ==========

    @Transactional
    public ReportResponse uploadDocument(MultipartFile file, String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Store file
        String filePath = fileStorageService.storeFile(file, userId);
        StorageType storageType = fileStorageService.getCurrentStorageType();

        // Extract text from document
        String extractedText = textExtractor.extractText(file);

        // Parse the extracted text into structured fields
        ReportDocumentParser.ParsedReportData parsedData = documentParser.parse(extractedText);
        log.info("Parsed document - hasData: {}, patient: {}",
                parsedData.hasData(), parsedData.getPatientName());

        // Create report with parsed data (use parsed values, fallback to defaults)
        Report report = Report.builder()
                .user(user)
                .originalFilename(file.getOriginalFilename())
                .filePath(filePath)
                .fileSize(file.getSize())
                .fileType(file.getContentType())
                .storageType(storageType)
                .extractedText(extractedText)
                // Populate from parsed data
                .patientName(parsedData.getPatientName())
                .patientId(parsedData.getPatientId())
                .patientAge(parsedData.getPatientAge())
                .patientSex(parsedData.getPatientSex())
                .scanDate(parsedData.getScanDate() != null ? parsedData.getScanDate() : LocalDate.now())
                .scanType(parsedData.getScanType())
                .reportType(parsedData.getReportType())
                .clinicalHistory(parsedData.getClinicalHistory())
                .findings(parsedData.getFindings() != null ? parsedData.getFindings()
                        : "Uploaded Document: " + file.getOriginalFilename())
                .impression(parsedData.getImpression())
                .recommendation(parsedData.getRecommendation())
                .build();

        Report savedReport = reportRepository.save(report);
        reportRepository.updateSearchVector(savedReport.getId());
        auditService.logAction(user, "document_uploaded", "Uploaded document: " + file.getOriginalFilename());

        return mapToResponse(savedReport);
    }

    /**
     * Batch upload multiple documents (UR-002)
     */
    @Transactional
    public BatchUploadResponse batchUploadDocuments(List<MultipartFile> files, String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<BatchUploadResponse.BatchUploadResult> results = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;

        for (MultipartFile file : files) {
            try {
                ReportResponse response = uploadDocument(file, userId);
                results.add(BatchUploadResponse.BatchUploadResult.builder()
                        .filename(file.getOriginalFilename())
                        .success(true)
                        .reportId(response.getId())
                        .build());
                successCount++;
            } catch (Exception e) {
                log.error("Failed to upload file: {}", file.getOriginalFilename(), e);
                results.add(BatchUploadResponse.BatchUploadResult.builder()
                        .filename(file.getOriginalFilename())
                        .success(false)
                        .errorMessage(e.getMessage())
                        .build());
                failureCount++;
            }
        }

        auditService.logAction(user, "batch_upload",
                String.format("Batch uploaded %d files (success: %d, failed: %d)",
                        files.size(), successCount, failureCount));

        return BatchUploadResponse.builder()
                .totalFiles(files.size())
                .successCount(successCount)
                .failureCount(failureCount)
                .results(results)
                .build();
    }

    // ========== Create/Update Operations ==========

    @Transactional
    public ReportResponse createReport(CreateReportRequest request, String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Report report = Report.builder()
                .user(user)
                .patientName(request.getPatientName())
                .patientAge(request.getPatientAge())
                .patientSex(request.getPatientSex())
                .patientId(request.getPatientId())
                .scanDate(request.getScanDate())
                .scanType(request.getScanType())
                .reportType(request.getReportType())
                .clinicalHistory(request.getClinicalHistory())
                .findings(request.getFindings())
                .impression(request.getImpression())
                .recommendation(request.getRecommendation())
                .tags(request.getTags())
                .build();

        Report savedReport = reportRepository.save(report);
        reportRepository.updateSearchVector(savedReport.getId());

        // Record template usage if a template was used
        if (request.getTemplateId() != null && !request.getTemplateId().isEmpty()) {
            templateService.recordTemplateUsage(request.getTemplateId());
        }

        auditService.logAction(user, "report_created", "Created report for patient: " + request.getPatientName());

        return mapToResponse(savedReport);
    }

    @Transactional
    public ReportResponse updateReport(String reportId, UpdateReportRequest request, String userId) {
        Report report = reportRepository.findByIdAndUserId(reportId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));

        User user = report.getUser();

        // Create version before updating (UR-031)
        createVersion(report, user, "Report updated");

        // Apply updates
        if (request.getPatientName() != null)
            report.setPatientName(request.getPatientName());
        if (request.getPatientAge() != null)
            report.setPatientAge(request.getPatientAge());
        if (request.getPatientSex() != null)
            report.setPatientSex(request.getPatientSex());
        if (request.getPatientId() != null)
            report.setPatientId(request.getPatientId());
        if (request.getScanDate() != null)
            report.setScanDate(request.getScanDate());
        if (request.getScanType() != null)
            report.setScanType(request.getScanType());
        if (request.getReportType() != null)
            report.setReportType(request.getReportType());
        if (request.getClinicalHistory() != null)
            report.setClinicalHistory(request.getClinicalHistory());
        if (request.getFindings() != null)
            report.setFindings(request.getFindings());
        if (request.getImpression() != null)
            report.setImpression(request.getImpression());
        if (request.getRecommendation() != null)
            report.setRecommendation(request.getRecommendation());
        if (request.getTags() != null)
            report.setTags(request.getTags());
        if (request.getIsFavorite() != null)
            report.setIsFavorite(request.getIsFavorite());

        Report updatedReport = reportRepository.save(report);
        reportRepository.updateSearchVector(updatedReport.getId());
        auditService.logAction(user, "report_updated", "Updated report: " + reportId);

        return mapToResponse(updatedReport);
    }

    @Transactional
    public void deleteReport(String reportId, String userId) {
        Report report = reportRepository.findByIdAndUserId(reportId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));

        reportRepository.delete(report);
        auditService.logAction(report.getUser(), "report_deleted", "Deleted report: " + reportId);
    }

    // ========== Version History (UR-031) ==========

    /**
     * Get version history for a report
     */
    @Transactional(readOnly = true)
    public Page<ReportVersionResponse> getVersionHistory(String reportId, String userId, Pageable pageable) {
        Report report = reportRepository.findByIdAndUserId(reportId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));

        return versionRepository.findByReportIdOrderByVersionNumberDesc(reportId, pageable)
                .map(this::mapToVersionResponse);
    }

    /**
     * Get a specific version of a report
     */
    @Transactional(readOnly = true)
    public ReportResponse getReportVersion(String reportId, Integer versionNumber, String userId) {
        Report report = reportRepository.findByIdAndUserId(reportId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));

        ReportVersion version = versionRepository.findByReportIdAndVersionNumber(reportId, versionNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Version not found"));

        // Parse the stored JSON back to report data
        try {
            ReportResponse response = objectMapper.readValue(version.getReportData(), ReportResponse.class);
            return response;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse version data", e);
        }
    }

    /**
     * Restore a report to a previous version
     */
    @Transactional
    public ReportResponse restoreVersion(String reportId, Integer versionNumber, String userId) {
        Report report = reportRepository.findByIdAndUserId(reportId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));

        ReportVersion version = versionRepository.findByReportIdAndVersionNumber(reportId, versionNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Version not found"));

        User user = report.getUser();

        // Create version of current state before restoring
        createVersion(report, user, "Before restoring to version " + versionNumber);

        // Parse the stored JSON and restore
        try {
            Map<String, Object> data = objectMapper.readValue(version.getReportData(), Map.class);

            if (data.get("patientName") != null)
                report.setPatientName((String) data.get("patientName"));
            if (data.get("patientId") != null)
                report.setPatientId((String) data.get("patientId"));
            if (data.get("clinicalHistory") != null)
                report.setClinicalHistory((String) data.get("clinicalHistory"));
            if (data.get("findings") != null)
                report.setFindings((String) data.get("findings"));
            if (data.get("impression") != null)
                report.setImpression((String) data.get("impression"));
            if (data.get("recommendation") != null)
                report.setRecommendation((String) data.get("recommendation"));

            Report restoredReport = reportRepository.save(report);
            reportRepository.updateSearchVector(restoredReport.getId());

            auditService.logAction(user, "report_restored",
                    String.format("Restored report %s to version %d", reportId, versionNumber));

            return mapToResponse(restoredReport);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to restore version", e);
        }
    }

    private void createVersion(Report report, User changedBy, String description) {
        int nextVersion = versionRepository.findMaxVersionNumber(report.getId()) + 1;

        try {
            // Create a JSON snapshot of the report
            Map<String, Object> reportData = new HashMap<>();
            reportData.put("patientName", report.getPatientName());
            reportData.put("patientId", report.getPatientId());
            reportData.put("patientAge", report.getPatientAge());
            reportData.put("patientSex", report.getPatientSex() != null ? report.getPatientSex().name() : null);
            reportData.put("scanDate", report.getScanDate() != null ? report.getScanDate().toString() : null);
            reportData.put("scanType", report.getScanType() != null ? report.getScanType().name() : null);
            reportData.put("reportType", report.getReportType() != null ? report.getReportType().name() : null);
            reportData.put("clinicalHistory", report.getClinicalHistory());
            reportData.put("findings", report.getFindings());
            reportData.put("impression", report.getImpression());
            reportData.put("recommendation", report.getRecommendation());
            reportData.put("tags", report.getTags());

            String jsonData = objectMapper.writeValueAsString(reportData);

            ReportVersion version = ReportVersion.builder()
                    .report(report)
                    .versionNumber(nextVersion)
                    .reportData(jsonData)
                    .changedBy(changedBy)
                    .changeDescription(description)
                    .build();

            versionRepository.save(version);
            log.debug("Created version {} for report {}", nextVersion, report.getId());
        } catch (JsonProcessingException e) {
            log.error("Failed to create version for report {}", report.getId(), e);
        }
    }

    // ========== Read Operations ==========

    @Transactional(readOnly = true)
    public Page<ReportResponse> searchReports(SearchReportsRequest request, String userId, Pageable pageable) {
        Pageable unsortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.unsorted());

        Page<Report> reports = reportRepository.searchReports(
                userId,
                request.getQuery(),
                request.getScanType() != null ? request.getScanType().name() : null,
                request.getReportType() != null ? request.getReportType().name() : null,
                request.getPatientName(),
                request.getFavoritesOnly() != null ? request.getFavoritesOnly() : false,
                unsortedPageable);

        return reports.map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public ReportResponse getReport(String reportId, String userId) {
        Report report = reportRepository.findByIdAndUserId(reportId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));
        return mapToResponse(report);
    }

    @Transactional(readOnly = true)
    public List<ReportSummaryResponse> getRecentReports(String userId) {
        return reportRepository.findTop10ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::mapToSummary)
                .collect(Collectors.toList());
    }

    // ========== Mappers ==========

    private ReportResponse mapToResponse(Report report) {
        return ReportResponse.builder()
                .id(report.getId())
                .userId(report.getUser().getId())
                .patientName(report.getPatientName())
                .patientAge(report.getPatientAge())
                .patientSex(report.getPatientSex())
                .patientId(report.getPatientId())
                .scanDate(report.getScanDate())
                .scanType(report.getScanType())
                .reportType(report.getReportType())
                .clinicalHistory(report.getClinicalHistory())
                .findings(report.getFindings())
                .impression(report.getImpression())
                .recommendation(report.getRecommendation())
                .originalFilename(report.getOriginalFilename())
                .filePath(report.getFilePath())
                .fileSize(report.getFileSize())
                .fileType(report.getFileType())
                .storageType(report.getStorageType())
                .tags(report.getTags())
                .isFavorite(report.getIsFavorite())
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getUpdatedAt())
                .build();
    }

    private ReportSummaryResponse mapToSummary(Report report) {
        String impressionPreview = report.getImpression();
        if (impressionPreview != null && impressionPreview.length() > 200) {
            impressionPreview = impressionPreview.substring(0, 200) + "...";
        }

        return ReportSummaryResponse.builder()
                .id(report.getId())
                .patientName(report.getPatientName())
                .patientAge(report.getPatientAge())
                .patientSex(report.getPatientSex())
                .scanDate(report.getScanDate())
                .scanType(report.getScanType())
                .reportType(report.getReportType())
                .impressionPreview(impressionPreview)
                .tags(report.getTags())
                .isFavorite(report.getIsFavorite())
                .hasFile(report.getFilePath() != null)
                .createdAt(report.getCreatedAt())
                .build();
    }

    private ReportVersionResponse mapToVersionResponse(ReportVersion version) {
        return ReportVersionResponse.builder()
                .id(version.getId())
                .reportId(version.getReport().getId())
                .versionNumber(version.getVersionNumber())
                .changeDescription(version.getChangeDescription())
                .changedByName(version.getChangedBy() != null ? version.getChangedBy().getFullName() : null)
                .changedByEmail(version.getChangedBy() != null ? version.getChangedBy().getEmail() : null)
                .createdAt(version.getCreatedAt())
                .build();
    }
}
