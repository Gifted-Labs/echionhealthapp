package com.giftedlabs.echoinhealthbackend.controller;

import com.giftedlabs.echoinhealthbackend.dto.common.ApiResponse;
import com.giftedlabs.echoinhealthbackend.dto.vault.SemanticSearchResponse;
import com.giftedlabs.echoinhealthbackend.dto.vault.TerminologySearchRequest;
import com.giftedlabs.echoinhealthbackend.dto.vault.TerminologySearchResponse;
import com.giftedlabs.echoinhealthbackend.security.CurrentUserService;
import com.giftedlabs.echoinhealthbackend.security.RoleGroups;
import com.giftedlabs.echoinhealthbackend.entity.ScanType;
import com.giftedlabs.echoinhealthbackend.service.SemanticVaultSearchService;
import com.giftedlabs.echoinhealthbackend.service.TerminologySearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI-assisted search.
 *
 * <p>Separate from {@code /generate-report} because searching is not authoring: these endpoints
 * answer "what is this called" and "where have I written about this before", and a client calls
 * them while the user is typing rather than when they submit. Grouping them here keeps the
 * authoring surface about producing a report.
 */
@RestController
@RequestMapping("/ai/search")
@RequiredArgsConstructor
@Tag(name = "AI Search", description = "AI-assisted terminology lookup and natural-language vault search")
@PreAuthorize(RoleGroups.CLINICAL)
public class AiSearchController {

    private final TerminologySearchService terminologySearchService;
    private final SemanticVaultSearchService semanticVaultSearchService;
    private final CurrentUserService currentUserService;

    @PostMapping("/terminology")
    @Operation(summary = "Search clinical terminology",
            description = "Looks up ultrasound and radiology terms: canonical spelling, definition, "
                    + "synonyms, terms it is commonly confused with, and the word used in a report "
                    + "sentence. Optionally biased by scan type. Consumes one AI credit per uncached "
                    + "lookup; repeated lookups of the same term are served from cache and cost "
                    + "nothing. Results are AI-generated drafting assistance, not a validated "
                    + "clinical reference.")
    public ResponseEntity<ApiResponse<TerminologySearchResponse>> searchTerminology(
            @Valid @RequestBody TerminologySearchRequest request,
            Authentication authentication) {

        return ResponseEntity.ok(ApiResponse.success(
                terminologySearchService.search(request, currentUserService.requireUser(authentication))));
    }

    @PostMapping("/vault")
    @Operation(summary = "Search the vault in plain language",
            description = "Finds templates by what they are about rather than by the words they "
                    + "contain: \"cases where the liver looked bright\" matches templates filed "
                    + "under hepatic steatosis. The query is expanded into clinical terminology, "
                    + "which is returned alongside the results so the caller can see what was "
                    + "actually searched for. Results are scoped to the caller's own vault and the "
                    + "templates shared with them, exactly as keyword search is. Consumes one AI "
                    + "credit per uncached expansion; the vault itself is never sent to a provider.")
    public ResponseEntity<ApiResponse<SemanticSearchResponse>> searchVault(
            @RequestParam String query,
            @RequestParam(required = false) ScanType scanType,
            @PageableDefault(size = 20) Pageable pageable,
            Authentication authentication) {

        return ResponseEntity.ok(ApiResponse.success(semanticVaultSearchService.search(
                query, scanType, currentUserService.requireUser(authentication), pageable)));
    }
}
