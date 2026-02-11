package com.giftedlabs.echoinhealthbackend.controller;

import com.giftedlabs.echoinhealthbackend.dto.common.ApiResponse;
import com.giftedlabs.echoinhealthbackend.dto.vault.*;
import com.giftedlabs.echoinhealthbackend.service.FileStorageService;
import com.giftedlabs.echoinhealthbackend.service.ReportDocxService;
import com.giftedlabs.echoinhealthbackend.service.ReportPdfService;
import com.giftedlabs.echoinhealthbackend.service.ReportService;
import com.giftedlabs.echoinhealthbackend.entity.Report;
import com.giftedlabs.echoinhealthbackend.repository.ReportRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/vault")
@RequiredArgsConstructor
@Tag(name = "Vault", description = "Report vault management APIs")
public class VaultController {

        private final ReportService reportService;
        private final FileStorageService fileStorageService;
        private final ReportPdfService reportPdfService;
        private final ReportDocxService reportDocxService;
        private final ReportRepository reportRepository;
        private final com.giftedlabs.echoinhealthbackend.repository.UserRepository userRepository;

        // ========== Upload Operations ==========

        @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @Operation(summary = "Upload document", description = "Upload a PDF or Word document to the vault")
        public ResponseEntity<ApiResponse<ReportResponse>> uploadDocument(
                        @Parameter(description = "Document file (PDF or Word)", required = true) @RequestParam("file") MultipartFile file,
                        Authentication authentication) {

                String userId = getUserId(authentication);
                ReportResponse response = reportService.uploadDocument(file, userId);

                return ResponseEntity.ok(ApiResponse.<ReportResponse>builder()
                                .success(true)
                                .message("Document uploaded and parsed successfully")
                                .data(response)
                                .build());
        }

        /**
         * Batch upload multiple documents (UR-002)
         */
        @PostMapping(value = "/upload-batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @Operation(summary = "Batch upload", description = "Upload multiple documents at once")
        public ResponseEntity<ApiResponse<BatchUploadResponse>> batchUpload(
                        @Parameter(description = "Multiple document files", required = true) @RequestParam("files") List<MultipartFile> files,
                        Authentication authentication) {

                String userId = getUserId(authentication);
                BatchUploadResponse response = reportService.batchUploadDocuments(files, userId);

                return ResponseEntity.ok(ApiResponse.<BatchUploadResponse>builder()
                                .success(true)
                                .message(String.format("Batch upload complete: %d/%d files successful",
                                                response.getSuccessCount(), response.getTotalFiles()))
                                .data(response)
                                .build());
        }

        // ========== Report CRUD ==========

        @PostMapping("/reports")
        @Operation(summary = "Create report", description = "Create a new report manually or from a template")
        public ResponseEntity<ApiResponse<ReportResponse>> createReport(
                        @Valid @RequestBody CreateReportRequest request,
                        Authentication authentication) {

                String userId = getUserId(authentication);
                ReportResponse response = reportService.createReport(request, userId);

                return ResponseEntity.ok(ApiResponse.<ReportResponse>builder()
                                .success(true)
                                .message("Report created successfully")
                                .data(response)
                                .build());
        }

        @PutMapping("/reports/{id}")
        @Operation(summary = "Update report", description = "Update an existing report (creates version history)")
        public ResponseEntity<ApiResponse<ReportResponse>> updateReport(
                        @PathVariable String id,
                        @RequestBody UpdateReportRequest request,
                        Authentication authentication) {

                String userId = getUserId(authentication);
                ReportResponse response = reportService.updateReport(id, request, userId);

                return ResponseEntity.ok(ApiResponse.<ReportResponse>builder()
                                .success(true)
                                .message("Report updated successfully")
                                .data(response)
                                .build());
        }

        @DeleteMapping("/reports/{id}")
        @Operation(summary = "Delete report", description = "Delete a report from the vault")
        public ResponseEntity<ApiResponse<Void>> deleteReport(
                        @PathVariable String id,
                        Authentication authentication) {

                String userId = getUserId(authentication);
                reportService.deleteReport(id, userId);

                return ResponseEntity.ok(ApiResponse.<Void>builder()
                                .success(true)
                                .message("Report deleted successfully")
                                .build());
        }

        // ========== Search & Retrieval ==========

        @PostMapping("/reports/search")
        @Operation(summary = "Search reports", description = "Search reports with filters")
        public ResponseEntity<ApiResponse<Page<ReportResponse>>> searchReports(
                        @RequestBody SearchReportsRequest request,
                        Pageable pageable,
                        Authentication authentication) {

                String userId = getUserId(authentication);
                Page<ReportResponse> reports = reportService.searchReports(request, userId, pageable);

                return ResponseEntity.ok(ApiResponse.<Page<ReportResponse>>builder()
                                .success(true)
                                .data(reports)
                                .build());
        }

        @GetMapping("/reports/{id}")
        @Operation(summary = "Get report", description = "Get a specific report by ID")
        public ResponseEntity<ApiResponse<ReportResponse>> getReport(
                        @PathVariable String id,
                        Authentication authentication) {

                String userId = getUserId(authentication);
                ReportResponse response = reportService.getReport(id, userId);

                return ResponseEntity.ok(ApiResponse.<ReportResponse>builder()
                                .success(true)
                                .data(response)
                                .build());
        }

        @GetMapping("/reports/recent")
        @Operation(summary = "Recent reports", description = "Get 10 most recent reports")
        public ResponseEntity<ApiResponse<List<ReportSummaryResponse>>> getRecentReports(
                        Authentication authentication) {

                String userId = getUserId(authentication);
                List<ReportSummaryResponse> reports = reportService.getRecentReports(userId);

                return ResponseEntity.ok(ApiResponse.<List<ReportSummaryResponse>>builder()
                                .success(true)
                                .data(reports)
                                .build());
        }

        // ========== Version History (UR-031) ==========

        @GetMapping("/reports/{id}/versions")
        @Operation(summary = "Version history", description = "Get version history for a report")
        public ResponseEntity<ApiResponse<Page<ReportVersionResponse>>> getVersionHistory(
                        @PathVariable String id,
                        Pageable pageable,
                        Authentication authentication) {

                String userId = getUserId(authentication);
                Page<ReportVersionResponse> versions = reportService.getVersionHistory(id, userId, pageable);

                return ResponseEntity.ok(ApiResponse.<Page<ReportVersionResponse>>builder()
                                .success(true)
                                .data(versions)
                                .build());
        }

        @GetMapping("/reports/{id}/versions/{versionNumber}")
        @Operation(summary = "Get version", description = "Get a specific version of a report")
        public ResponseEntity<ApiResponse<ReportResponse>> getReportVersion(
                        @PathVariable String id,
                        @PathVariable Integer versionNumber,
                        Authentication authentication) {

                String userId = getUserId(authentication);
                ReportResponse response = reportService.getReportVersion(id, versionNumber, userId);

                return ResponseEntity.ok(ApiResponse.<ReportResponse>builder()
                                .success(true)
                                .data(response)
                                .build());
        }

        @PostMapping("/reports/{id}/versions/{versionNumber}/restore")
        @Operation(summary = "Restore version", description = "Restore a report to a previous version")
        public ResponseEntity<ApiResponse<ReportResponse>> restoreVersion(
                        @PathVariable String id,
                        @PathVariable Integer versionNumber,
                        Authentication authentication) {

                String userId = getUserId(authentication);
                ReportResponse response = reportService.restoreVersion(id, versionNumber, userId);

                return ResponseEntity.ok(ApiResponse.<ReportResponse>builder()
                                .success(true)
                                .message("Report restored to version " + versionNumber)
                                .data(response)
                                .build());
        }

        // ========== Export Operations ==========

        @GetMapping("/reports/{id}/download")
        @Operation(summary = "Download file", description = "Download the original uploaded file")
        public ResponseEntity<byte[]> downloadFile(
                        @PathVariable String id,
                        Authentication authentication) throws IOException {

                String userId = getUserId(authentication);
                ReportResponse report = reportService.getReport(id, userId);

                if (report.getFilePath() == null) {
                        throw new com.giftedlabs.echoinhealthbackend.exception.ResourceNotFoundException(
                                        "No file associated");
                }

                byte[] fileContent = fileStorageService.downloadFile(report.getFilePath());
                String filename = report.getOriginalFilename() != null ? report.getOriginalFilename() : "report";

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.parseMediaType(
                                report.getFileType() != null ? report.getFileType()
                                                : MediaType.APPLICATION_OCTET_STREAM_VALUE));
                headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
                headers.setContentLength(fileContent.length);

                return ResponseEntity.ok().headers(headers).body(fileContent);
        }

        @GetMapping("/reports/{id}/download-pdf")
        @Operation(summary = "Download PDF", description = "Generate and download report as PDF")
        public ResponseEntity<byte[]> downloadPdf(
                        @PathVariable String id,
                        Authentication authentication) throws IOException {

                String userId = getUserId(authentication);
                Report report = reportRepository.findByIdAndUserId(id, userId)
                                .orElseThrow(() -> new com.giftedlabs.echoinhealthbackend.exception.ResourceNotFoundException(
                                                "Report not found"));

                byte[] pdfContent = reportPdfService.generatePdf(report);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_PDF);
                headers.setContentDisposition(ContentDisposition.attachment()
                                .filename("report_" + id + ".pdf").build());
                headers.setContentLength(pdfContent.length);

                return ResponseEntity.ok().headers(headers).body(pdfContent);
        }

        /**
         * Download report as DOCX (UR-025)
         */
        @GetMapping("/reports/{id}/download-docx")
        @Operation(summary = "Download DOCX", description = "Generate and download report as Word document")
        public ResponseEntity<byte[]> downloadDocx(
                        @PathVariable String id,
                        Authentication authentication) throws IOException {

                String userId = getUserId(authentication);
                Report report = reportRepository.findByIdAndUserId(id, userId)
                                .orElseThrow(() -> new com.giftedlabs.echoinhealthbackend.exception.ResourceNotFoundException(
                                                "Report not found"));

                byte[] docxContent = reportDocxService.generateDocx(report);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
                headers.setContentDisposition(ContentDisposition.attachment()
                                .filename("report_" + id + ".docx").build());
                headers.setContentLength(docxContent.length);

                return ResponseEntity.ok().headers(headers).body(docxContent);
        }

        @GetMapping("/reports/{id}/print")
        @Operation(summary = "Print PDF", description = "Generate print-friendly PDF")
        public ResponseEntity<byte[]> printPdf(
                        @PathVariable String id,
                        Authentication authentication) throws IOException {

                String userId = getUserId(authentication);
                Report report = reportRepository.findByIdAndUserId(id, userId)
                                .orElseThrow(() -> new com.giftedlabs.echoinhealthbackend.exception.ResourceNotFoundException(
                                                "Report not found"));

                byte[] pdfContent = reportPdfService.generatePdf(report);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_PDF);
                headers.setContentDisposition(ContentDisposition.inline()
                                .filename("report_" + id + ".pdf").build());

                return ResponseEntity.ok().headers(headers).body(pdfContent);
        }

        // ========== Helper ==========

        private String getUserId(Authentication authentication) {
                UserDetails userDetails = (UserDetails) authentication.getPrincipal();
                String email = userDetails.getUsername();
                return findUserIdByEmail(email);
        }

        private String findUserIdByEmail(String email) {
                return userRepository.findByEmail(email)
                                .orElseThrow(() -> new com.giftedlabs.echoinhealthbackend.exception.ResourceNotFoundException(
                                                "User not found"))
                                .getId();
        }
}
