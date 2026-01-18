package com.giftedlabs.echoinhealthbackend.controller;

import com.giftedlabs.echoinhealthbackend.dto.common.ApiResponse;
import com.giftedlabs.echoinhealthbackend.dto.vault.*;
import com.giftedlabs.echoinhealthbackend.service.FileStorageService;
import com.giftedlabs.echoinhealthbackend.service.ReportPdfService;
import com.giftedlabs.echoinhealthbackend.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
@Tag(name = "SonoVault", description = "Document management and report APIs")
public class VaultController {

        private final ReportService reportService;
        private final FileStorageService fileStorageService;
        private final ReportPdfService reportPdfService;

        @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @Operation(summary = "Upload document", description = "Upload a Word or PDF document for extraction and storage")
        public ResponseEntity<ApiResponse<ReportResponse>> uploadDocument(
                        @Parameter(description = "Document file (PDF or Word)", required = true) @RequestParam("file") MultipartFile file,
                        Authentication authentication) {

                String userId = getUserId(authentication);
                ReportResponse report = reportService.uploadDocument(file, userId);

                return ResponseEntity.ok(ApiResponse.<ReportResponse>builder()
                                .success(true)
                                .message("Document uploaded successfully")
                                .data(report)
                                .build());
        }

        @PostMapping("/reports")
        @Operation(summary = "Create report", description = "Create a new structured report")
        public ResponseEntity<ApiResponse<ReportResponse>> createReport(
                        @Valid @RequestBody CreateReportRequest request,
                        Authentication authentication) {

                String userId = getUserId(authentication);
                ReportResponse report = reportService.createReport(request, userId);

                return ResponseEntity.ok(ApiResponse.<ReportResponse>builder()
                                .success(true)
                                .message("Report created successfully")
                                .data(report)
                                .build());
        }

        @PutMapping("/reports/{id}")
        @Operation(summary = "Update report", description = "Update an existing report")
        public ResponseEntity<ApiResponse<ReportResponse>> updateReport(
                        @PathVariable String id,
                        @RequestBody UpdateReportRequest request,
                        Authentication authentication) {

                String userId = getUserId(authentication);
                ReportResponse report = reportService.updateReport(id, request, userId);

                return ResponseEntity.ok(ApiResponse.<ReportResponse>builder()
                                .success(true)
                                .message("Report updated successfully")
                                .data(report)
                                .build());
        }

        @DeleteMapping("/reports/{id}")
        @Operation(summary = "Delete report", description = "Delete a report permanently")
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

        @PostMapping("/search")
        @Operation(summary = "Search reports", description = "Full-text search for reports with filters")
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
        @Operation(summary = "Get report", description = "Get details of a specific report")
        public ResponseEntity<ApiResponse<ReportResponse>> getReport(
                        @PathVariable String id,
                        Authentication authentication) {

                String userId = getUserId(authentication);
                ReportResponse report = reportService.getReport(id, userId);

                return ResponseEntity.ok(ApiResponse.<ReportResponse>builder()
                                .success(true)
                                .data(report)
                                .build());
        }

        @GetMapping("/reports/recent")
        @Operation(summary = "Get recent reports", description = "Get the most recent reports for the user")
        public ResponseEntity<ApiResponse<List<ReportSummaryResponse>>> getRecentReports(
                        Authentication authentication) {

                String userId = getUserId(authentication);
                List<ReportSummaryResponse> reports = reportService.getRecentReports(userId);

                return ResponseEntity.ok(ApiResponse.<List<ReportSummaryResponse>>builder()
                                .success(true)
                                .data(reports)
                                .build());
        }

        @GetMapping("/reports/{id}/download")
        @Operation(summary = "Download original file", description = "Download the ORIGINAL uploaded file (PDF/Word). Returns 404 if report was created manually.")
        public ResponseEntity<?> downloadFile(
                        @PathVariable String id,
                        Authentication authentication) {

                String userId = getUserId(authentication);
                ReportResponse report = reportService.getReport(id, userId);

                // Check if this report has an associated file
                if (report.getFilePath() == null || report.getFilePath().isBlank()) {
                        return ResponseEntity.status(404)
                                        .body(ApiResponse.<Void>builder()
                                                        .success(false)
                                                        .message("No original file associated with this report. Use /pdf to generate a PDF from the report data.")
                                                        .build());
                }

                try {
                        // Get file stream
                        java.io.InputStream inputStream = fileStorageService.getFileStream(report.getFilePath(),
                                        report.getStorageType());
                        InputStreamResource resource = new InputStreamResource(inputStream);

                        return ResponseEntity.ok()
                                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                                        "attachment; filename=\"" + report.getOriginalFilename() + "\"")
                                        .contentType(MediaType.parseMediaType(
                                                        report.getFileType() != null ? report.getFileType()
                                                                        : "application/octet-stream"))
                                        .contentLength(report.getFileSize())
                                        .body(resource);
                } catch (IOException e) {
                        return ResponseEntity.status(500)
                                        .body(ApiResponse.<Void>builder()
                                                        .success(false)
                                                        .message("Failed to retrieve file: " + e.getMessage())
                                                        .build());
                }
        }

        @GetMapping("/reports/{id}/pdf")
        @Operation(summary = "Download PDF", description = "Generate and DOWNLOAD the report as a PDF file (attachment)")
        public ResponseEntity<byte[]> downloadPdf(
                        @PathVariable String id,
                        Authentication authentication) throws IOException {

                String userId = getUserId(authentication);
                byte[] pdfBytes = reportPdfService.generateReportPdf(id, userId);
                String filename = reportPdfService.generateFilename(id, userId);

                return ResponseEntity.ok()
                                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                                .contentType(MediaType.APPLICATION_PDF)
                                .contentLength(pdfBytes.length)
                                .body(pdfBytes);
        }

        @GetMapping("/reports/{id}/print")
        @Operation(summary = "Print/Preview PDF", description = "Generate and display the report as PDF INLINE (for browser preview/print)")
        public ResponseEntity<byte[]> printPdf(
                        @PathVariable String id,
                        Authentication authentication) throws IOException {

                String userId = getUserId(authentication);
                byte[] pdfBytes = reportPdfService.generateReportPdf(id, userId);

                return ResponseEntity.ok()
                                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"report.pdf\"")
                                .contentType(MediaType.APPLICATION_PDF)
                                .contentLength(pdfBytes.length)
                                .body(pdfBytes);
        }

        /**
         * Helper to extract User ID from Authentication
         * This relies on CustomUserDetailsService adding the ID, or we need to look it
         * up.
         * Since UserDetails usually just has username, we might need to lookup by email
         * OR update CustomUserDetailsService to put ID in a custom UserDetails
         * implementation.
         * 
         * For now, I'll assume the username IS the email, and look up the ID via a
         * service/repository if needed,
         * BUT wait, we don't have the ID in standard UserDetails.
         * 
         * Optimization: Let's assume the username is the email, and I'll lookup user by
         * email in the service layer?
         * NO, the service layer methods take `userId`.
         * 
         * Let's change the Service methods to take `email` instead of `userId`?
         * OR lookup the user in the controller.
         * 
         * Better approach: The `Authentication` principal is
         * `org.springframework.security.core.userdetails.User`.
         * Let's fetch the actual User entity in the Controller for now to get the ID.
         * OR just change Service methods to accept email.
         * 
         * Checking ReportService:
         * `User user = userRepository.findById(userId)`
         * 
         * I should probably add `userRepository.findByEmail(email)` call here to get
         * the ID.
         */
        private String getUserId(Authentication authentication) {
                UserDetails userDetails = (UserDetails) authentication.getPrincipal();
                String email = userDetails.getUsername();

                // We need to look up the user ID.
                // Ideally we'd cache this or put it in the token/principal.
                // For this implementation phase, I'll inject UserRepository to lookup.
                return findUserIdByEmail(email);
        }

        // Quick workaround lookup
        private final com.giftedlabs.echoinhealthbackend.repository.UserRepository userRepository;

        private String findUserIdByEmail(String email) {
                return userRepository.findByEmail(email)
                                .orElseThrow(
                                                () -> new com.giftedlabs.echoinhealthbackend.exception.UserNotFoundException(
                                                                "User not found"))
                                .getId();
        }
}
