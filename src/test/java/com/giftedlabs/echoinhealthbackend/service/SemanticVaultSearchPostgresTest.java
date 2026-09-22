package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.dto.vault.TemplateResponse;
import com.giftedlabs.echoinhealthbackend.entity.Organization;
import com.giftedlabs.echoinhealthbackend.entity.ReportTemplate;
import com.giftedlabs.echoinhealthbackend.entity.Role;
import com.giftedlabs.echoinhealthbackend.entity.ScanType;
import com.giftedlabs.echoinhealthbackend.entity.SubscriptionTier;
import com.giftedlabs.echoinhealthbackend.entity.User;
import com.giftedlabs.echoinhealthbackend.repository.OrganizationRepository;
import com.giftedlabs.echoinhealthbackend.repository.ReportTemplateRepository;
import com.giftedlabs.echoinhealthbackend.repository.UserRepository;
import com.giftedlabs.echoinhealthbackend.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The ranking half of natural-language vault search.
 *
 * <p>This covers the query, not the expansion: given a set of clinical terms, does the right
 * template come back, in the right order, and only from inside the tenant boundary. The
 * expansion that produces those terms is exercised in {@link TerminologySearchServiceTest}, where
 * the provider can be mocked.
 *
 * <p>PostgreSQL-backed because the ranking is {@code unnest} over a {@code text[]} parameter with
 * a correlated count in the ORDER BY. H2 will not execute that, and a search that ranks wrongly
 * is indistinguishable from one that works until a user looks at the second result.
 */
class SemanticVaultSearchPostgresTest extends PostgresIntegrationTest {

    @Autowired private TemplateService templateService;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ReportTemplateRepository templateRepository;

    private Organization organization;
    private User owner;
    private User outsider;

    @BeforeEach
    void seed() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        organization = organizationRepository.save(Organization.builder()
                .name("Semantic Org " + suffix)
                .hospitalName("Semantic Hospital")
                .subscriptionTier(SubscriptionTier.PRO)
                .build());

        Organization otherOrg = organizationRepository.save(Organization.builder()
                .name("Other Org " + suffix)
                .hospitalName("Other Hospital")
                .subscriptionTier(SubscriptionTier.BASIC)
                .build());

        owner = userRepository.save(user("owner-" + suffix + "@test.local", organization));
        outsider = userRepository.save(user("outsider-" + suffix + "@test.local", otherOrg));

        // Hits two expanded terms: hydronephrosis in the name, pelvicalyceal in the findings.
        templateRepository.save(template("Hydronephrosis follow-up", ScanType.ABDOMINAL,
                "Renal", "Pelvicalyceal dilatation with cortical thinning.", owner, organization));

        // Hits one.
        templateRepository.save(template("Routine renal survey", ScanType.ABDOMINAL,
                "Renal", "Kidneys normal in size. No hydronephrosis.", owner, organization));

        // Hits none of the renal vocabulary.
        templateRepository.save(template("Thyroid nodule protocol", ScanType.THYROID,
                "Small Parts", "Solitary hypoechoic nodule in the right lobe.", owner, organization));

        // Same vocabulary, different tenant. Must never surface.
        templateRepository.save(template("Hydronephrosis protocol", ScanType.ABDOMINAL,
                "Renal", "Pelvicalyceal dilatation.", outsider, otherOrg));
    }

    @Test
    @DisplayName("a template matching more of the expanded terms ranks above one matching fewer")
    void rankingFavoursTemplatesMatchingMoreTerms() {
        Page<TemplateResponse> results = search(List.of("hydronephrosis", "pelvicalyceal"));

        assertThat(results.getTotalElements()).isEqualTo(2);
        assertThat(results.getContent().getFirst().getName()).isEqualTo("Hydronephrosis follow-up");
        assertThat(results.getContent().get(1).getName()).isEqualTo("Routine renal survey");
    }

    @Test
    @DisplayName("expanded terms match content the query never contained")
    void termsMatchFindingsNotJustNames() {
        // "pelvicalyceal" appears only in default findings, nowhere in any template name.
        Page<TemplateResponse> results = search(List.of("pelvicalyceal"));

        assertThat(results.getTotalElements()).isEqualTo(1);
        assertThat(results.getContent().getFirst().getName()).isEqualTo("Hydronephrosis follow-up");
    }

    @Test
    @DisplayName("templates matching none of the terms are excluded rather than ranked last")
    void nonMatchingTemplatesAreExcluded() {
        Page<TemplateResponse> results = search(List.of("hydronephrosis", "pelvicalyceal"));

        assertThat(results.getContent())
                .extracting(TemplateResponse::getName)
                .doesNotContain("Thyroid nodule protocol");
    }

    @Test
    @DisplayName("another tenant's identically-worded template is never returned")
    void tenantBoundaryHolds() {
        Page<TemplateResponse> mine = search(List.of("hydronephrosis"));
        assertThat(mine.getContent())
                .extracting(TemplateResponse::getName)
                .doesNotContain("Hydronephrosis protocol");

        // And the reverse: the outsider sees only their own.
        Page<TemplateResponse> theirs = templateService.semanticSearch(
                List.of("hydronephrosis"), null, outsider.getId(), PageRequest.of(0, 20));
        assertThat(theirs.getTotalElements()).isEqualTo(1);
        assertThat(theirs.getContent().getFirst().getName()).isEqualTo("Hydronephrosis protocol");
    }

    @Test
    @DisplayName("a scan type filter narrows the result set")
    void scanTypeNarrowsResults() {
        assertThat(search(List.of("hydronephrosis"), ScanType.THYROID).getTotalElements()).isZero();
        assertThat(search(List.of("hydronephrosis"), ScanType.ABDOMINAL).getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("a term nothing matches returns an empty page rather than everything")
    void unmatchedTermReturnsNothing() {
        assertThat(search(List.of("qwertyosis")).getTotalElements()).isZero();
    }

    // ============================================================================ fixtures

    private Page<TemplateResponse> search(List<String> terms) {
        return search(terms, null);
    }

    private Page<TemplateResponse> search(List<String> terms, ScanType scanType) {
        return templateService.semanticSearch(terms, scanType, owner.getId(), PageRequest.of(0, 20));
    }

    private User user(String email, Organization org) {
        return User.builder()
                .email(email)
                .passwordHash("x")
                .firstName("Test")
                .lastName("User")
                .role(Role.SONOGRAPHER)
                .organization(org)
                .build();
    }

    private ReportTemplate template(String name, ScanType scanType, String category,
                                    String findings, User user, Organization org) {
        return ReportTemplate.builder()
                .organization(org)
                .user(user)
                .name(name)
                .description("Template for " + name)
                .defaultFindings(findings)
                .scanType(scanType)
                .category(category)
                .tags(new String[] { "routine" })
                .isActive(true)
                .isFavorite(false)
                .usageCount(0)
                .build();
    }
}
