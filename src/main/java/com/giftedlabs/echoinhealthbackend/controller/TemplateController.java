package com.giftedlabs.echoinhealthbackend.controller;

import com.giftedlabs.echoinhealthbackend.dto.common.ApiResponse;
import com.giftedlabs.echoinhealthbackend.dto.vault.CreateTemplateRequest;
import com.giftedlabs.echoinhealthbackend.dto.vault.TemplateAnalyticsResponse;
import com.giftedlabs.echoinhealthbackend.dto.vault.TemplateResponse;
import com.giftedlabs.echoinhealthbackend.service.TemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/vault/templates")
@RequiredArgsConstructor
@Tag(name = "Templates", description = "Report template management APIs")
public class TemplateController {

        private final TemplateService templateService;
        private final com.giftedlabs.echoinhealthbackend.repository.UserRepository userRepository;

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

        @DeleteMapping("/{id}")
        @Operation(summary = "Delete template", description = "Delete a custom template")
        public ResponseEntity<ApiResponse<Void>> deleteTemplate(
                        @PathVariable String id,
                        Authentication authentication) {

                String userId = getUserId(authentication);
                templateService.deleteTemplate(id, userId);

                return ResponseEntity.ok(ApiResponse.<Void>builder()
                                .success(true)
                                .message("Template deleted successfully")
                                .build());
        }

        private String getUserId(Authentication authentication) {
                UserDetails userDetails = (UserDetails) authentication.getPrincipal();
                return userRepository.findByEmail(userDetails.getUsername())
                                .orElseThrow(
                                                () -> new com.giftedlabs.echoinhealthbackend.exception.UserNotFoundException(
                                                                "User not found"))
                                .getId();
        }
}
