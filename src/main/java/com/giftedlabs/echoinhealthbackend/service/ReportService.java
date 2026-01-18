package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.dto.vault.*;
import com.giftedlabs.echoinhealthbackend.entity.*;
import com.giftedlabs.echoinhealthbackend.exception.ResourceNotFoundException;
import com.giftedlabs.echoinhealthbackend.repository.ReportRepository;
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

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final TextExtractor textExtractor;
    private final ReportDocumentParser documentParser;
    private final AuditService auditService;

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
                .scanDate(parsedData.getScanDate() != null ? parsedData.getScanDate() : java.time.LocalDate.now())
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
        auditService.logAction(user, "report_created", "Created report for patient: " + request.getPatientName());

        return mapToResponse(savedReport);
    }

    @Transactional
    public ReportResponse updateReport(String reportId, UpdateReportRequest request, String userId) {
        Report report = reportRepository.findByIdAndUserId(reportId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));

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
        auditService.logAction(report.getUser(), "report_updated", "Updated report: " + reportId);

        return mapToResponse(updatedReport);
    }

    @Transactional
    public void deleteReport(String reportId, String userId) {
        Report report = reportRepository.findByIdAndUserId(reportId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));

        // Delete file if exists (async ideally, but sync for now)
        // TODO: Implement file deletion in FileStorageService

        reportRepository.delete(report);
        auditService.logAction(report.getUser(), "report_deleted", "Deleted report: " + reportId);
    }

    @Transactional(readOnly = true)
    public Page<ReportResponse> searchReports(SearchReportsRequest request, String userId, Pageable pageable) {
        // The native query already has its own ORDER BY clause, so we must NOT pass
        // sorting in Pageable
        // Otherwise Spring Data JPA will append another ORDER BY causing a SQL syntax
        // error
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
}
