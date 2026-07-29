package com.giftedlabs.echoinhealthbackend.controller;

import com.giftedlabs.echoinhealthbackend.dto.common.ApiResponse;
import com.giftedlabs.echoinhealthbackend.dto.vault.CreateTemplateRequest;
import com.giftedlabs.echoinhealthbackend.dto.vault.SearchTemplatesRequest;
import com.giftedlabs.echoinhealthbackend.dto.vault.TemplateAnalyticsResponse;
import com.giftedlabs.echoinhealthbackend.dto.vault.TemplateResponse;
import com.giftedlabs.echoinhealthbackend.dto.vault.TemplateVersionResponse;
import com.giftedlabs.echoinhealthbackend.dto.vault.UpdateTemplateRequest;
import com.giftedlabs.echoinhealthbackend.dto.vault.ShareTemplateRequest;
import com.giftedlabs.echoinhealthbackend.dto.vault.TemplateBulkUploadResponse;
import com.giftedlabs.echoinhealthbackend.security.AuthenticatedUser;
import com.giftedlabs.echoinhealthbackend.security.CurrentUserService;
import com.giftedlabs.echoinhealthbackend.service.TemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

import java.util.List;

@RestController
@RequestMapping("/vault/templates")
@RequiredArgsConstructor
@Tag(name = "Templates", description = "Report template management APIs")
@PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'SONOGRAPHER', 'RADIOLOGIST', 'PHYSICIAN', 'ADMIN', 'SUPER_ADMIN')")
public class TemplateController {

        private final TemplateService templateService;
        private final CurrentUserService currentUserService;

        @PostMapping
        @Operation(summary = "Create template", description = "Create a new custom report template")
        public ResponseEntity<ApiResponse<TemplateResponse>> createTemplate(
                        @Valid @RequestBody CreateTemplateRequest request,
                        Authentication authentication) {

                String userId = getUserId(authentication);
                TemplateResponse template = templateService.createTemplate(request, userId);

                return ResponseEntity.ok(ApiResponse.<TemplateResponse>builder()
                                .success(true)
                                .message("Template created successfully")
                                .data(template)
                                .build());
        }

        @PutMapping("/{id}")
        @Operation(summary = "Update template", description = "Update an existing template (UR-051)")
        public ResponseEntity<ApiResponse<TemplateResponse>> updateTemplate(
                        @PathVariable String id,
                        @Valid @RequestBody UpdateTemplateRequest request,
                        Authentication authentication) {

                String userId = getUserId(authentication);
                TemplateResponse template = templateService.updateTemplate(id, request, userId);

                return ResponseEntity.ok(ApiResponse.<TemplateResponse>builder()
                                .success(true)
                                .message("Template updated successfully")
                                .data(template)
                                .build());
        }

        @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @Operation(summary = "Upload template file", description = "Upload a template from PDF/DOCX/TXT (UR-047/UR-049)")
        public ResponseEntity<ApiResponse<TemplateResponse>> uploadTemplate(
                        @RequestParam("file") MultipartFile file,
                        @RequestParam(value = "name", required = false) String name,
                        @RequestParam(value = "scanType", required = false) String scanType,
                        Authentication authentication) {

                String userId = getUserId(authentication);
                TemplateResponse template = templateService.uploadTemplate(file, name, scanType, userId);

                return ResponseEntity.ok(ApiResponse.<TemplateResponse>builder()
                                .success(true)
                                .message("Template uploaded and parsed successfully")
                                .data(template)
                                .build());
        }

        @PostMapping(value = "/bulk-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @Operation(summary = "Bulk upload templates", description = "Upload multiple template files (UR-048)")
        public ResponseEntity<ApiResponse<TemplateBulkUploadResponse>> bulkUploadTemplates(
                        @RequestParam("files") List<MultipartFile> files,
                        Authentication authentication) {

                String userId = getUserId(authentication);
                TemplateBulkUploadResponse response = templateService.bulkUploadTemplates(files, userId);

                return ResponseEntity.ok(ApiResponse.<TemplateBulkUploadResponse>builder()
                                .success(true)
                                .message("Bulk upload completed")
                                .data(response)
                                .build());
        }

        @PostMapping(value = "/import/json", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @Operation(summary = "Import JSON templates", description = "Import templates from a JSON file (UR-048)")
        public ResponseEntity<ApiResponse<TemplateBulkUploadResponse>> importJsonTemplates(
                        @RequestParam("file") MultipartFile file,
                        Authentication authentication) {

                String userId = getUserId(authentication);
                TemplateBulkUploadResponse response = templateService.importTemplatesFromJson(file, userId);

                return ResponseEntity.ok(ApiResponse.<TemplateBulkUploadResponse>builder()
                                .success(true)
                                .message("JSON import completed")
                                .data(response)
                                .build());
        }

        @PostMapping(value = "/import/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @Operation(summary = "Import CSV templates", description = "Import templates from a CSV file (UR-048)")
        public ResponseEntity<ApiResponse<TemplateBulkUploadResponse>> importCsvTemplates(
                        @RequestParam("file") MultipartFile file,
                        Authentication authentication) {

                String userId = getUserId(authentication);
                TemplateBulkUploadResponse response = templateService.importTemplatesFromCsv(file, userId);

                return ResponseEntity.ok(ApiResponse.<TemplateBulkUploadResponse>builder()
                                .success(true)
                                .message("CSV import completed")
                                .data(response)
                                .build());
        }

        @PostMapping("/{id}/share")
        @Operation(summary = "Share template", description = "Share a template with colleagues (UR-058)")
        public ResponseEntity<ApiResponse<Void>> shareTemplate(
                        @PathVariable String id,
                        @Valid @RequestBody ShareTemplateRequest request,
                        Authentication authentication) {

                String userId = getUserId(authentication);
                templateService.shareTemplate(id, request.getRecipientUserIds(), userId);

                return ResponseEntity.ok(ApiResponse.<Void>builder()
                                .success(true)
                                .message("Template shared successfully")
                                .build());
        }

        @GetMapping
        @Operation(summary = "Get templates", description = "Get all available templates")
        public ResponseEntity<ApiResponse<List<TemplateResponse>>> getTemplates(
                        Authentication authentication) {

                String userId = getUserId(authentication);
                List<TemplateResponse> templates = templateService.getAllTemplates(userId);

                return ResponseEntity.ok(ApiResponse.<List<TemplateResponse>>builder()
                                .success(true)
                                .data(templates)
                                .build());
        }

        @PostMapping("/search")
        @Operation(summary = "Search templates", description = "Search templates by keyword, scan type, tag, category, date, favorites, and sort order")
        public ResponseEntity<ApiResponse<Page<TemplateResponse>>> searchTemplates(
                        @RequestBody SearchTemplatesRequest request,
                        Pageable pageable,
                        Authentication authentication) {

                String userId = getUserId(authentication);
                Page<TemplateResponse> templates = templateService.searchTemplates(request, userId, pageable);

                return ResponseEntity.ok(ApiResponse.<Page<TemplateResponse>>builder()
                                .success(true)
                                .data(templates)
                                .build());
        }

        @GetMapping("/favorites")
        @Operation(summary = "Favorite templates", description = "Get favorite templates for the current user")
        public ResponseEntity<ApiResponse<List<TemplateResponse>>> getFavoriteTemplates(Authentication authentication) {
                List<TemplateResponse> templates = templateService.getFavoriteTemplates(getUserId(authentication));
                return ResponseEntity.ok(ApiResponse.success(templates));
        }

        @GetMapping("/recent")
        @Operation(summary = "Recent templates", description = "Get the 10 most recently used templates")
        public ResponseEntity<ApiResponse<List<TemplateResponse>>> getRecentTemplates(Authentication authentication) {
                List<TemplateResponse> templates = templateService.getRecentTemplates(getUserId(authentication));
                return ResponseEntity.ok(ApiResponse.success(templates));
        }

        @PutMapping("/{id}/favorite")
        @Operation(summary = "Set favorite", description = "Mark or unmark a template as favorite")
        public ResponseEntity<ApiResponse<TemplateResponse>> setFavorite(
                        @PathVariable String id,
                        @RequestParam boolean value,
                        Authentication authentication) {

                TemplateResponse template = templateService.setFavorite(id, value, getUserId(authentication));
                return ResponseEntity.ok(ApiResponse.success(template));
        }

        /**
         * Duplicate a template (UR-032)
         */
        @PostMapping("/{id}/duplicate")
        @Operation(summary = "Duplicate template", description = "Create a copy of an existing template")
        public ResponseEntity<ApiResponse<TemplateResponse>> duplicateTemplate(
                        @PathVariable String id,
                        @RequestParam(required = false) String newName,
                        Authentication authentication) {

                String userId = getUserId(authentication);
                TemplateResponse template = templateService.duplicateTemplate(id, userId, newName);

                return ResponseEntity.ok(ApiResponse.<TemplateResponse>builder()
                                .success(true)
                                .message("Template duplicated successfully")
                                .data(template)
                                .build());
        }

        /**
         * Get template usage analytics (UR-034)
         */
        @GetMapping("/analytics")
        @Operation(summary = "Template analytics", description = "Get usage statistics for templates")
        public ResponseEntity<ApiResponse<List<TemplateAnalyticsResponse>>> getAnalytics(
                        Authentication authentication) {

                String userId = getUserId(authentication);
                List<TemplateAnalyticsResponse> analytics = templateService.getTemplateAnalytics(userId);

                return ResponseEntity.ok(ApiResponse.<List<TemplateAnalyticsResponse>>builder()
                                .success(true)
                                .data(analytics)
                                .build());
        }

        @GetMapping("/{id}/versions")
        @Operation(summary = "Template version history", description = "Get version history for a template")
        public ResponseEntity<ApiResponse<Page<TemplateVersionResponse>>> getVersionHistory(
                        @PathVariable String id,
                        Pageable pageable,
                        Authentication authentication) {

                Page<TemplateVersionResponse> versions = templateService.getVersionHistory(
                                id,
                                getUserId(authentication),
                                pageable);
                return ResponseEntity.ok(ApiResponse.success(versions));
        }

        @PostMapping("/{id}/versions/{versionNumber}/restore")
        @Operation(summary = "Restore template version", description = "Restore a template to a previous version")
        public ResponseEntity<ApiResponse<TemplateResponse>> restoreVersion(
                        @PathVariable String id,
                        @PathVariable Integer versionNumber,
                        Authentication authentication) {

                TemplateResponse template = templateService.restoreVersion(id, versionNumber, getUserId(authentication));
                return ResponseEntity.ok(ApiResponse.<TemplateResponse>builder()
                                .success(true)
                                .message("Template restored to version " + versionNumber)
                                .data(template)
                                .build());
        }

        @DeleteMapping("/{id}")
        @Operation(summary = "Delete template", description = "Delete a custom template. Shared templates are blocked by default unless cascadeSharedAccess=true is provided.")
        public ResponseEntity<ApiResponse<Void>> deleteTemplate(
                        @PathVariable String id,
                        @RequestParam(defaultValue = "false") boolean cascadeSharedAccess,
                        Authentication authentication) {

                String userId = getUserId(authentication);
                templateService.deleteTemplate(id, userId, cascadeSharedAccess);

                String message = cascadeSharedAccess
                                ? "Template deleted successfully and shared access removed"
                                : "Template deleted successfully";

                return ResponseEntity.ok(ApiResponse.<Void>builder()
                                .success(true)
                                .message(message)
                                .build());
        }

        private String getUserId(Authentication authentication) {
                AuthenticatedUser user = currentUserService.requirePrincipal(authentication);
                return user.getUserId();
        }
}
