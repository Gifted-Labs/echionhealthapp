package com.giftedlabs.echoinhealthbackend.controller;

import com.giftedlabs.echoinhealthbackend.dto.common.ApiResponse;
import com.giftedlabs.echoinhealthbackend.dto.vault.TerminologySearchRequest;
import com.giftedlabs.echoinhealthbackend.dto.vault.TerminologySearchResponse;
import com.giftedlabs.echoinhealthbackend.security.CurrentUserService;
import com.giftedlabs.echoinhealthbackend.security.RoleGroups;
import com.giftedlabs.echoinhealthbackend.service.TerminologySearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
@Tag(name = "AI Search", description = "AI-assisted terminology lookup")
@PreAuthorize(RoleGroups.CLINICAL)
public class AiSearchController {

    private final TerminologySearchService terminologySearchService;
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
}
