package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.dto.vault.SearchTemplatesRequest;
import com.giftedlabs.echoinhealthbackend.dto.vault.TemplateResponse;
import com.giftedlabs.echoinhealthbackend.entity.Organization;
import com.giftedlabs.echoinhealthbackend.entity.ReportTemplate;
import com.giftedlabs.echoinhealthbackend.entity.Role;
import com.giftedlabs.echoinhealthbackend.entity.ScanType;
import com.giftedlabs.echoinhealthbackend.entity.SharedTemplate;
import com.giftedlabs.echoinhealthbackend.entity.SubscriptionTier;
import com.giftedlabs.echoinhealthbackend.entity.User;
import com.giftedlabs.echoinhealthbackend.repository.OrganizationRepository;
import com.giftedlabs.echoinhealthbackend.repository.ReportTemplateRepository;
import com.giftedlabs.echoinhealthbackend.repository.SharedTemplateRepository;
import com.giftedlabs.echoinhealthbackend.repository.UserRepository;
import com.giftedlabs.echoinhealthbackend.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vault search now runs as one indexed PostgreSQL query instead of loading every template the
 * user can see and filtering in Java streams. The query uses PostgreSQL array containment for
 * tag filtering, so it cannot be exercised on the H2 test profile — this runs on real
 * PostgreSQL through {@link PostgresIntegrationTest}.
 */
class VaultSearchPostgresTest extends PostgresIntegrationTest {

    @Autowired
    private TemplateService templateService;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ReportTemplateRepository templateRepository;
    @Autowired
    private SharedTemplateRepository sharedTemplateRepository;

    private Organization organization;
    private User owner;
    private User colleague;

    @BeforeEach
    void seed() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        organization = organizationRepository.save(Organization.builder()
                .name("Search Org " + suffix)
                .hospitalName("Search Hospital")
                .subscriptionTier(SubscriptionTier.PRO)
                .build());

        owner = userRepository.save(user("owner-" + suffix + "@test.local"));
        colleague = userRepository.save(user("colleague-" + suffix + "@test.local"));

        templateRepository.save(template("Liver protocol", ScanType.ABDOMINAL, "Abdominal",
                new String[] { "normal", "routine" }, owner, true));
        templateRepository.save(template("Thyroid nodule protocol", ScanType.THYROID, "Small Parts",
                new String[] { "abnormal", "complex" }, owner, false));
        templateRepository.save(template("Obstetric dating scan", ScanType.OBSTETRIC_EARLY, "Obstetric",
                new String[] { "routine" }, owner, false));
    }

    private User user(String email) {
        return User.builder()
                .email(email)
                .passwordHash("x")
                .firstName("Test")
                .lastName("User")
                .role(Role.SONOGRAPHER)
                .organization(organization)
                .build();
    }

    private ReportTemplate template(String name, ScanType scanType, String category,
                                    String[] tags, User user, boolean favorite) {
        return ReportTemplate.builder()
                .organization(organization)
                .user(user)
                .name(name)
                .description("Template for " + name)
                .scanType(scanType)
                .category(category)
                .tags(tags)
                .isActive(true)
                .isFavorite(favorite)
                .usageCount(0)
                .build();
    }

    private Page<TemplateResponse> search(SearchTemplatesRequest request, User asUser) {
        return templateService.searchTemplates(request, asUser.getId(), PageRequest.of(0, 20));
    }

    @Test
    void keywordSearchMatchesNameAndDescription() {
        Page<TemplateResponse> results = search(
                SearchTemplatesRequest.builder().keyword("thyroid").build(), owner);

        assertEquals(1, results.getTotalElements());
        assertEquals("Thyroid nodule protocol", results.getContent().get(0).getName());
    }

    @Test
    void scanTypeAndCategoryFiltersNarrowResults() {
        assertEquals(1, search(
                SearchTemplatesRequest.builder().scanType(ScanType.ABDOMINAL).build(), owner)
                .getTotalElements());

        assertEquals(1, search(
                SearchTemplatesRequest.builder().category("obstetric").build(), owner)
                .getTotalElements());
    }

    /** Tag filtering relies on PostgreSQL {@code = ANY(tags)} array containment. */
    @Test
    void tagFilterUsesArrayContainment() {
        Page<TemplateResponse> routine = search(
                SearchTemplatesRequest.builder().tag("routine").build(), owner);
        assertEquals(2, routine.getTotalElements());

        Page<TemplateResponse> complex = search(
                SearchTemplatesRequest.builder().tag("complex").build(), owner);
        assertEquals(1, complex.getTotalElements());
        assertEquals("Thyroid nodule protocol", complex.getContent().get(0).getName());

        assertEquals(0, search(
                SearchTemplatesRequest.builder().tag("nonexistent").build(), owner)
                .getTotalElements());
    }

    @Test
    void favouritesFilterAppliesInTheDatabase() {
        Page<TemplateResponse> favourites = search(
                SearchTemplatesRequest.builder().favoritesOnly(true).build(), owner);

        assertEquals(1, favourites.getTotalElements());
        assertEquals("Liver protocol", favourites.getContent().get(0).getName());
    }

    /**
     * Paging must be applied by the database. The previous implementation fetched everything
     * and sliced the list afterwards, so totalElements was right but the cost was not.
     */
    @Test
    void resultsArePagedAndSortedByTheDatabase() {
        Page<TemplateResponse> firstPage = templateService.searchTemplates(
                SearchTemplatesRequest.builder().sortBy("name").sortDirection("asc").build(),
                owner.getId(), PageRequest.of(0, 2));

        assertEquals(3, firstPage.getTotalElements());
        assertEquals(2, firstPage.getContent().size());
        assertEquals(List.of("Liver protocol", "Obstetric dating scan"),
                firstPage.getContent().stream().map(TemplateResponse::getName).toList());

        Page<TemplateResponse> secondPage = templateService.searchTemplates(
                SearchTemplatesRequest.builder().sortBy("name").sortDirection("asc").build(),
                owner.getId(), PageRequest.of(1, 2));
        assertEquals(1, secondPage.getContent().size());
        assertEquals("Thyroid nodule protocol", secondPage.getContent().get(0).getName());
    }

    @Test
    void searchIsScopedToTheCallerAndIncludesTemplatesSharedWithThem() {
        // A colleague sees none of the owner's templates by default.
        assertEquals(0, search(SearchTemplatesRequest.builder().build(), colleague)
                .getTotalElements());

        ReportTemplate shared = templateRepository
                .findAll().stream()
                .filter(t -> "Liver protocol".equals(t.getName()))
                .findFirst()
                .orElseThrow();
        sharedTemplateRepository.save(SharedTemplate.builder()
                .organization(organization)
                .template(shared)
                .owner(owner)
                .recipient(colleague)
                .build());

        Page<TemplateResponse> visible = search(SearchTemplatesRequest.builder().build(), colleague);
        assertEquals(1, visible.getTotalElements());
        assertEquals("Liver protocol", visible.getContent().get(0).getName());

        // Sharing one template must not expose the rest of the owner's library.
        assertFalse(visible.getContent().stream()
                .anyMatch(t -> "Thyroid nodule protocol".equals(t.getName())));
    }

    @Test
    void softDeletedTemplatesDisappearFromSearchButKeepCountingStorage() {
        ReportTemplate withBlob = templateRepository.save(ReportTemplate.builder()
                .organization(organization)
                .user(owner)
                .name("Uploaded protocol")
                .scanType(ScanType.ABDOMINAL)
                .filePath("some/object/key.pdf")
                .fileSize(4096L)
                .isActive(false)
                .blobDeleted(false)
                .usageCount(0)
                .build());

        assertTrue(search(SearchTemplatesRequest.builder().keyword("Uploaded").build(), owner)
                .getContent().isEmpty(), "inactive templates must not appear in search");

        // Storage accounting keys off blobDeleted, not isActive: the bytes are still there.
        assertTrue(templateRepository.sumFileSizeByOrganizationId(organization.getId()) >= 4096L,
                "a soft-deleted template whose blob still exists must keep counting");

        withBlob.setBlobDeleted(true);
        templateRepository.save(withBlob);
        assertEquals(0L, templateRepository.sumFileSizeByOrganizationId(organization.getId()));
    }
}
