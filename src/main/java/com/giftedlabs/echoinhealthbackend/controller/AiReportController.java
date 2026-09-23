package com.giftedlabs.echoinhealthbackend.controller;

import com.giftedlabs.echoinhealthbackend.dto.common.ApiResponse;
import com.giftedlabs.echoinhealthbackend.dto.vault.AiSuggestImpressionRequest;
import com.giftedlabs.echoinhealthbackend.dto.vault.AiSuggestImpressionResponse;
import com.giftedlabs.echoinhealthbackend.dto.vault.GenerateAiReportRequest;
import com.giftedlabs.echoinhealthbackend.dto.vault.GenerateAiReportResponse;
import com.giftedlabs.echoinhealthbackend.dto.vault.GrammarCheckRequest;
import com.giftedlabs.echoinhealthbackend.dto.vault.GrammarCheckResponse;
import com.giftedlabs.echoinhealthbackend.security.CurrentUserService;
import com.giftedlabs.echoinhealthbackend.service.AiReportGenerationService;
import com.giftedlabs.echoinhealthbackend.service.GrammarCheckService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import com.giftedlabs.echoinhealthbackend.security.RoleGroups;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/generate-report")
@RequiredArgsConstructor
@Tag(name = "AI Report Generation", description = "AI-assisted ultrasound report generation endpoints")
@PreAuthorize(RoleGroups.CLINICAL)
public class AiReportController {

    private final AiReportGenerationService aiReportGenerationService;
    private final GrammarCheckService grammarCheckService;
    private final CurrentUserService currentUserService;

    @PostMapping("/grammar-check")
    @Operation(summary = "Auto-Grammar Check (Pro/Ultimate)",
            description = "Reviews report prose for grammar, spelling and register without altering "
                    + "clinical content. Returns suggested edits; nothing is applied automatically.")
    public ResponseEntity<ApiResponse<GrammarCheckResponse>> checkGrammar(
            @Valid @RequestBody GrammarCheckRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                "Grammar check completed",
                grammarCheckService.checkGrammar(request, currentUserService.requireUser(authentication))));
    }

    @PostMapping
    @Operation(summary = "Generate structured report", description = "Generate findings, impression, and recommendations from raw scan notes")
    public ResponseEntity<ApiResponse<GenerateAiReportResponse>> generateReport(
            @Valid @RequestBody GenerateAiReportRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                "AI report generated successfully",
                aiReportGenerationService.generateReport(request, currentUserService.requireUser(authentication))));
    }

    @PostMapping("/suggest-impression")
    @Operation(summary = "Suggest impression", description = "Generate an AI-assisted impression from findings")
    public ResponseEntity<ApiResponse<AiSuggestImpressionResponse>> suggestImpression(
            @Valid @RequestBody AiSuggestImpressionRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                "AI impression suggested successfully",
                aiReportGenerationService.suggestImpression(request, currentUserService.requireUser(authentication))));
    }
}
