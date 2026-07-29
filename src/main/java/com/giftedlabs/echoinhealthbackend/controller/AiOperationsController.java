package com.giftedlabs.echoinhealthbackend.controller;

import com.giftedlabs.echoinhealthbackend.dto.admin.AiProviderStatusResponse;
import com.giftedlabs.echoinhealthbackend.dto.admin.AiUsageReportResponse;
import com.giftedlabs.echoinhealthbackend.dto.common.ApiResponse;
import com.giftedlabs.echoinhealthbackend.security.CurrentUserService;
import com.giftedlabs.echoinhealthbackend.service.AiUsageReportingService;
import com.giftedlabs.echoinhealthbackend.service.ai.AiProviderVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/ai")
@RequiredArgsConstructor
@Tag(name = "AI Operations", description = "AI provider health and usage reporting")
@PreAuthorize("hasAnyRole('HOSPITAL_ADMIN', 'ADMIN', 'SUPER_ADMIN')")
public class AiOperationsController {

    private final AiProviderVerificationService verificationService;
    private final AiUsageReportingService usageReportingService;
    private final CurrentUserService currentUserService;

    @PostMapping("/verify")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Verify AI providers",
            description = "Performs a real round trip to each configured provider and reports "
                    + "whether it returned parseable structured output. Does not consume AI credits.")
    public ResponseEntity<ApiResponse<List<AiProviderStatusResponse>>> verifyProviders() {
        return ResponseEntity.ok(ApiResponse.success(
                "AI provider verification completed", verificationService.verifyAll()));
    }

    @GetMapping("/usage")
    @Operation(summary = "AI usage report",
            description = "Success rate, fallback rate, latency and estimated cost derived from "
                    + "recorded AI generation events")
    public ResponseEntity<ApiResponse<AiUsageReportResponse>> getUsage(
            @RequestParam(defaultValue = "30") int windowDays,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(usageReportingService.getUsageReport(
                currentUserService.requireUser(authentication), windowDays)));
    }
}
