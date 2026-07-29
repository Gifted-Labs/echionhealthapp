package com.giftedlabs.echoinhealthbackend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.giftedlabs.echoinhealthbackend.dto.vault.*;
import com.giftedlabs.echoinhealthbackend.entity.*;
import com.giftedlabs.echoinhealthbackend.exception.FieldValidationException;
import com.giftedlabs.echoinhealthbackend.exception.ResourceNotFoundException;
import com.giftedlabs.echoinhealthbackend.repository.ReportRepository;
import com.giftedlabs.echoinhealthbackend.repository.ReportVersionRepository;
import com.giftedlabs.echoinhealthbackend.repository.UserRepository;
import com.giftedlabs.echoinhealthbackend.util.ReportDocumentParser;
import com.giftedlabs.echoinhealthbackend.util.TextExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    private final ReportRepository reportRepository;
    private final ReportVersionRepository versionRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final TextExtractor textExtractor;
    private final ReportDocumentParser documentParser;
    private final AuditService auditService;
    private final TemplateService templateService;
    private final ObjectMapper objectMapper;
    private final SignatureService signatureService;
    private final ReportAuthoringHelperService reportAuthoringHelperService;
    private final BillingService billingService;
    private final FileValidationService fileValidationService;

    // ========== Upload Operations ==========

    @Transactional
    public ReportResponse uploadDocument(MultipartFile file, String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String detectedContentType = fileValidationService.requireAllowedContentType(
                file,
                java.util.Set.of(
                        "application/pdf",
                        "application/msword",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "text/plain"),
                "Document must be PDF, DOC, DOCX, or TXT");
        billingService.assertStorageCapacity(user.getOrganization(), file.getSize());

        // Store file
        String filePath = fileStorageService.storeFile(file, user.getOrganizationId(), userId);
        StorageType storageType = fileStorageService.getCurrentStorageType();

        // Extract text from document
        String extractedText = textExtractor.extractText(file);

        // Parse the extracted text into structured fields
        ReportDocumentParser.ParsedReportData parsedData = documentParser.parse(extractedText);
        log.info("Parsed document - hasData: {}", parsedData.hasData());

        // Create report with parsed data (use parsed values, fallback to defaults)
        Report report = Report.builder()
                .organization(user.getOrganization())
                .user(user)
                .originalFilename(file.getOriginalFilename())
                .filePath(filePath)
                .fileSize(file.getSize())
                .fileType(detectedContentType)
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
                .status("DRAFT")
                .lastAutoSaveAt(LocalDateTime.now())
                .build();

        Report savedReport = reportRepository.save(report);
        updateSearchVectorSafely(savedReport.getId());
        auditService.logAction(user, "document_uploaded", "Uploaded source document");

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
        ReportTemplate template = resolveTemplate(request.getTemplateId(), userId);
        String findings = firstNonBlank(request.getFindings(), template != null ? template.getDefaultFindings() : null);
        String impression = firstNonBlank(request.getImpression(), template != null ? template.getDefaultImpression() : null);
        Map<String, String> validationErrors = new HashMap<>();
        if (request.getPatientName() == null || request.getPatientName().isBlank()) {
            validationErrors.put("patientName", "Patient name is required");
        }
        if (request.getPatientAge() == null) {
            validationErrors.put("patientAge", "Patient age is required");
        }
        if (request.getScanDate() == null) {
            validationErrors.put("scanDate", "Scan date is required");
        }
        if (findings == null || findings.isBlank()) {
            validationErrors.put("findings", "Findings are required");
        }
        if (!validationErrors.isEmpty()) {
            throw new FieldValidationException("Mandatory report fields must be completed before draft creation", validationErrors);
        }

        Report report = Report.builder()
                .organization(user.getOrganization())
                .user(user)
                .patientName(request.getPatientName())
                .patientAge(request.getPatientAge())
                .patientSex(request.getPatientSex())
                .patientId(request.getPatientId())
                .scanDate(request.getScanDate())
                .scanType(request.getScanType() != null ? request.getScanType() : template != null ? template.getScanType() : null)
                .reportType(request.getReportType() != null ? request.getReportType() : template != null ? template.getReportType() : null)
                .clinicalHistory(request.getClinicalHistory())
                .findings(findings)
                .impression(impression)
                .recommendation(normalizeRecommendationText(request.getRecommendation(), request.getRecommendationOptions()))
                .structuredFindings(writeStructuredFindings(request.getStructuredFindings()))
                .recommendationOptions(request.getRecommendationOptions())
                .tags(request.getTags())
                .isAiGenerated(Boolean.TRUE.equals(request.getIsAiGenerated()))
                .processingTimeSeconds(request.getProcessingTimeSeconds())
                .status("DRAFT")
                .lastAutoSaveAt(LocalDateTime.now())
                .build();

        Report savedReport = reportRepository.save(report);
        updateSearchVectorSafely(savedReport.getId());

        // Record template usage if a template was used
        if (request.getTemplateId() != null && !request.getTemplateId().isEmpty()) {
            templateService.recordTemplateUsage(request.getTemplateId(), userId);
        }

        auditService.logAction(user, "report_created", "Created report " + savedReport.getId());

        return mapToResponse(savedReport);
    }

    @Transactional
    public ReportResponse updateReport(String reportId, UpdateReportRequest request, String userId) {
        Report report = reportRepository.findByIdAndUserIdAndOrganizationId(reportId, userId, currentOrganizationId(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));

        User user = report.getUser();

        // Create version before updating (UR-031)
        createVersion(report, user, "Report updated");

        // Record whether a clinician actually changed the AI's clinical prose. This is what
        // makes the AI acceptance/revision metrics measured rather than assumed.
        markAiOutputEditedIfClinicalTextChanged(report, request.getFindings(),
                request.getImpression(), request.getRecommendation());

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
        if (request.getStructuredFindings() != null)
            report.setStructuredFindings(writeStructuredFindings(request.getStructuredFindings()));
        if (request.getRecommendationOptions() != null) {
            report.setRecommendationOptions(request.getRecommendationOptions());
            report.setRecommendation(normalizeRecommendationText(report.getRecommendation(), request.getRecommendationOptions()));
        }
        if (request.getTags() != null)
            report.setTags(request.getTags());
        if (request.getIsFavorite() != null)
            report.setIsFavorite(request.getIsFavorite());

        Report updatedReport = reportRepository.save(report);
        updateSearchVectorSafely(updatedReport.getId());
        auditService.logAction(user, "report_updated", "Updated report: " + reportId);

        return mapToResponse(updatedReport);
    }

    @Transactional
    public void deleteReport(String reportId, String userId) {
        Report report = reportRepository.findByIdAndUserIdAndOrganizationId(reportId, userId, currentOrganizationId(userId))
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
        Report report = reportRepository.findByIdAndUserIdAndOrganizationId(reportId, userId, currentOrganizationId(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));

        return versionRepository.findByReportIdOrderByVersionNumberDesc(reportId, pageable)
                .map(this::mapToVersionResponse);
    }

    /**
     * Get a specific version of a report
     */
    @Transactional(readOnly = true)
    public ReportResponse getReportVersion(String reportId, Integer versionNumber, String userId) {
        Report report = reportRepository.findByIdAndUserIdAndOrganizationId(reportId, userId, currentOrganizationId(userId))
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
        Report report = reportRepository.findByIdAndUserIdAndOrganizationId(reportId, userId, currentOrganizationId(userId))
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
            if (data.get("structuredFindings") != null)
                report.setStructuredFindings((String) data.get("structuredFindings"));
            if (data.get("recommendationOptions") != null) {
                List<String> options = (List<String>) data.get("recommendationOptions");
                report.setRecommendationOptions(options.toArray(String[]::new));
            }

            Report restoredReport = reportRepository.save(report);
            updateSearchVectorSafely(restoredReport.getId());

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
            reportData.put("structuredFindings", report.getStructuredFindings());
            reportData.put("recommendationOptions", report.getRecommendationOptions());
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
                currentOrganizationId(userId),
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
        Report report = reportRepository.findByIdAndUserIdAndOrganizationId(reportId, userId, currentOrganizationId(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));
        return mapToResponse(report);
    }

    @Transactional
    public ReportResponse autoSaveReport(String reportId, AutoSaveReportRequest request, String userId) {
        Report report = reportRepository.findByIdAndUserIdAndOrganizationId(reportId, userId, currentOrganizationId(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));

        if (request.getClinicalHistory() != null)
            report.setClinicalHistory(request.getClinicalHistory());
        if (request.getFindings() != null)
            report.setFindings(request.getFindings());
        if (request.getImpression() != null)
            report.setImpression(request.getImpression());
        if (request.getRecommendation() != null)
            report.setRecommendation(request.getRecommendation());
        if (request.getStructuredFindings() != null)
            report.setStructuredFindings(writeStructuredFindings(request.getStructuredFindings()));
        if (request.getRecommendationOptions() != null) {
            report.setRecommendationOptions(request.getRecommendationOptions());
            report.setRecommendation(normalizeRecommendationText(report.getRecommendation(), request.getRecommendationOptions()));
        }

        report.setLastAutoSaveAt(LocalDateTime.now());
        Report saved = reportRepository.save(report);
        updateSearchVectorSafely(saved.getId());
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public HelperToolsResponse getHelperTools(HelperToolsRequest request) {
        return reportAuthoringHelperService.buildHelpers(request.getScanType(), request.getFindings());
    }

    @Transactional
    public ReportResponse finalizeReport(String reportId, FinalizeReportRequest request, String userId) {
        Report report = reportRepository.findByIdAndUserIdAndOrganizationId(reportId, userId, currentOrganizationId(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));
        User user = report.getUser();

        Map<String, String> validationErrors = new HashMap<>();
        if (report.getPatientName() == null || report.getPatientName().isBlank()) {
            validationErrors.put("patientName", "Patient name is required");
        }
        if (report.getPatientAge() == null) {
            validationErrors.put("patientAge", "Patient age is required");
        }
        if (report.getScanDate() == null) {
            validationErrors.put("scanDate", "Scan date is required");
        }
        if (report.getScanType() == null) {
            validationErrors.put("scanType", "Scan type is required");
        }
        if (report.getFindings() == null || report.getFindings().isBlank()) {
            validationErrors.put("findings", "Findings are required");
        }

        if (!validationErrors.isEmpty()) {
            throw new FieldValidationException("Mandatory report fields must be completed before finalization",
                    validationErrors);
        }

        Signature signature = signatureService.requireOwnedSignature(user, request.getSignatureId());

        report.setAppliedSignatureId(signature.getId());
        report.setSignatoryName(user.getFullName());
        report.setSignatoryDesignation(request.getDesignation());
        report.setStatus("FINALIZED");
        report.setFinalizedAt(java.time.LocalDateTime.now());

        Report saved = reportRepository.save(report);
        auditService.logAction(user, "report_finalized",
                String.format("Finalized report %s with signature %s", saved.getId(), signature.getId()));
        auditService.logAction(user, "signature_applied",
                String.format("Applied signature %s to report %s", signature.getId(), saved.getId()));
        return mapToResponse(saved);
    }

    /**
     * Generate a pre-finalization preview of a report (UR-040).
     * Does not mutate state — only reads data and validates.
     */
    @Transactional(readOnly = true)
    public PreviewReportResponse previewReport(String reportId, PreviewReportRequest request, String userId) {
        Report report = reportRepository.findByIdAndUserIdAndOrganizationId(reportId, userId, currentOrganizationId(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));
        User user = report.getUser();
        Organization org = report.getOrganization();

        // Validate mandatory fields
        Map<String, String> validationErrors = new HashMap<>();
        if (report.getPatientName() == null || report.getPatientName().isBlank()) {
            validationErrors.put("patientName", "Patient name is required");
        }
        if (report.getPatientAge() == null) {
            validationErrors.put("patientAge", "Patient age is required");
        }
        if (report.getScanDate() == null) {
            validationErrors.put("scanDate", "Scan date is required");
        }
        if (report.getScanType() == null) {
            validationErrors.put("scanType", "Scan type is required");
        }
        if (report.getFindings() == null || report.getFindings().isBlank()) {
            validationErrors.put("findings", "Findings are required");
        }

        // Resolve signature for preview
        Signature signature = signatureService.requireOwnedSignature(user, request.getSignatureId());
        boolean signatureImageAvailable = false;
        try {
            byte[] sigBytes = fileStorageService.downloadFile(signature.getImageUrl());
            signatureImageAvailable = sigBytes != null && sigBytes.length > 0;
        } catch (Exception e) {
            log.warn("Could not load signature image for preview: {}", e.getMessage());
        }

        return PreviewReportResponse.builder()
                .reportId(report.getId())
                // Organization Branding
                .hospitalName(org != null && org.getHospitalName() != null ? org.getHospitalName() : "Echion Health")
                .hospitalAddress(org != null ? org.getAddress() : null)
                .hospitalPhone(org != null ? org.getPhone() : null)
                .hospitalEmail(org != null ? org.getEmail() : null)
                .hospitalWebsite(org != null ? org.getWebsite() : null)
                .letterheadUrl(org != null ? org.getLetterheadUrl() : null)
                .usingDefaultLetterhead(org == null || org.getLetterheadUrl() == null)
                // Patient Demographics
                .patientName(report.getPatientName())
                .patientAge(report.getPatientAge())
                .patientSex(report.getPatientSex())
                .patientId(report.getPatientId())
                // Scan Details
                .scanDate(report.getScanDate())
                .scanType(report.getScanType())
                .reportType(report.getReportType())
                // Report Content
                .clinicalHistory(report.getClinicalHistory())
                .findings(report.getFindings())
                .impression(report.getImpression())
                .recommendation(report.getRecommendation())
                .structuredFindings(readStructuredFindings(report.getStructuredFindings()))
                .recommendationOptions(report.getRecommendationOptions())
                // Signature Preview
                .signatureId(signature.getId())
                .signatureLabel(signature.getLabel())
                .signatureImageAvailable(signatureImageAvailable)
                .signatoryName(user.getFullName())
                .signatoryDesignation(request.getDesignation())
                // Validation
                .readyToFinalize(validationErrors.isEmpty())
                .validationErrors(validationErrors.isEmpty() ? null : validationErrors)
                // Metadata
                .previewGeneratedAt(LocalDateTime.now())
                .build();
    }

    @Transactional(readOnly = true)
    public List<ReportSummaryResponse> getRecentReports(String userId) {
        return reportRepository.findTop10ByUserIdAndOrganizationIdOrderByCreatedAtDesc(userId, currentOrganizationId(userId)).stream()
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
                .structuredFindings(readStructuredFindings(report.getStructuredFindings()))
                .recommendationOptions(report.getRecommendationOptions())
                .originalFilename(report.getOriginalFilename())
                .filePath(report.getFilePath())
                .fileSize(report.getFileSize())
                .fileType(report.getFileType())
                .storageType(report.getStorageType())
                .tags(report.getTags())
                .isFavorite(report.getIsFavorite())
                .status(report.getStatus())
                .appliedSignatureId(report.getAppliedSignatureId())
                .signatoryName(report.getSignatoryName())
                .signatoryDesignation(report.getSignatoryDesignation())
                .finalizedAt(report.getFinalizedAt())
                .lastAutoSaveAt(report.getLastAutoSaveAt())
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

    private String currentOrganizationId(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"))
                .getOrganizationId();
    }

    private ReportTemplate resolveTemplate(String templateId, String userId) {
        if (templateId == null || templateId.isBlank()) {
            return null;
        }
        return templateService.getAccessibleTemplate(templateId, userId);
    }

    private String writeStructuredFindings(Map<String, Object> structuredFindings) {
        try {
            return structuredFindings == null ? null : objectMapper.writeValueAsString(structuredFindings);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Structured findings could not be serialized");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readStructuredFindings(String structuredFindings) {
        try {
            return structuredFindings == null || structuredFindings.isBlank()
                    ? null
                    : objectMapper.readValue(structuredFindings, Map.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Structured findings could not be parsed", e);
        }
    }

    /**
     * Flags an AI-drafted report as edited when the incoming values differ from what the AI
     * produced. Only the clinical prose counts — correcting a patient name is not a revision
     * of the AI's output.
     */
    private void markAiOutputEditedIfClinicalTextChanged(Report report, String findings,
            String impression, String recommendation) {
        if (!Boolean.TRUE.equals(report.getIsAiGenerated())
                || Boolean.TRUE.equals(report.getAiOutputEdited())) {
            return;
        }
        if (changed(report.getFindings(), findings)
                || changed(report.getImpression(), impression)
                || changed(report.getRecommendation(), recommendation)) {
            report.setAiOutputEdited(true);
        }
    }

    private boolean changed(String current, String incoming) {
        return incoming != null && !incoming.equals(current);
    }

    private String normalizeRecommendationText(String freeText, String[] selectedOptions) {
        List<String> parts = new ArrayList<>();
        if (selectedOptions != null) {
            for (String option : selectedOptions) {
                if (option != null && !option.isBlank()) {
                    parts.add(option.trim());
                }
            }
        }
        if (freeText != null && !freeText.isBlank()) {
            parts.add(freeText.trim());
        }
        return parts.isEmpty() ? freeText : String.join("\n", parts);
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback;
    }

    private void updateSearchVectorSafely(String reportId) {
        if (!isPostgresDatasource()) {
            log.debug("Skipping search vector update for report {} on datasource {}", reportId, datasourceUrl);
            return;
        }
        try {
            reportRepository.updateSearchVector(reportId);
        } catch (Exception ex) {
            log.warn("Search vector update skipped for report {}: {}", reportId, ex.getMessage());
        }
    }

    private boolean isPostgresDatasource() {
        return datasourceUrl != null && datasourceUrl.startsWith("jdbc:postgresql:");
    }
}
