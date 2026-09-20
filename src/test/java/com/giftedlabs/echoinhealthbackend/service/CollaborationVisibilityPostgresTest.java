package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.dto.collaboration.ShareScanRequest;
import com.giftedlabs.echoinhealthbackend.dto.collaboration.SharedScanResponse;
import com.giftedlabs.echoinhealthbackend.entity.*;
import com.giftedlabs.echoinhealthbackend.repository.CollaborationNotificationRepository;
import com.giftedlabs.echoinhealthbackend.repository.OrganizationRepository;
import com.giftedlabs.echoinhealthbackend.repository.ReportRepository;
import com.giftedlabs.echoinhealthbackend.repository.UserRepository;
import com.giftedlabs.echoinhealthbackend.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SonoShare visibility and pagination.
 *
 * <p>"Shared with me" used to run three separately-paginated queries, concatenate their page
 * contents and wrap the result in a {@code PageImpl} whose total was the merged slice's size. Page
 * counts were wrong and items were duplicated across pages or skipped. Department and org-wide
 * shares also notified nobody, because the recipient list was only ever populated for
 * SPECIFIC_COLLEAGUES. These tests pin all of that down against real PostgreSQL.
 */
class CollaborationVisibilityPostgresTest extends PostgresIntegrationTest {

    @Autowired
    private CollaborationService collaborationService;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ReportRepository reportRepository;
    @Autowired
    private CollaborationNotificationRepository notificationRepository;

    private Organization organization;
    private Organization otherOrganization;
    private User owner;
    private User radiologyColleague;
    private User cardiologyColleague;
    private User outsider;

    @BeforeEach
    void seed() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        organization = organizationRepository.save(Organization.builder()
                .name("Share Org " + suffix)
                .hospitalName("Share Hospital")
                .subscriptionTier(SubscriptionTier.PRO)
                .build());

        otherOrganization = organizationRepository.save(Organization.builder()
                .name("Other Org " + suffix)
                .hospitalName("Other Hospital")
                .subscriptionTier(SubscriptionTier.PRO)
                .build());

        owner = userRepository.save(user(organization, "owner-" + suffix, Role.SONOGRAPHER, "Radiology"));
        radiologyColleague = userRepository.save(
                user(organization, "radiology-" + suffix, Role.RADIOLOGIST, "Radiology"));
        cardiologyColleague = userRepository.save(
                user(organization, "cardiology-" + suffix, Role.PHYSICIAN, "Cardiology"));
        outsider = userRepository.save(
                user(otherOrganization, "outsider-" + suffix, Role.RADIOLOGIST, "Radiology"));
    }

    @Test
    @DisplayName("specific-colleague share reaches only the named colleague, and notifies them")
    void specificColleagueShare() {
        collaborationService.shareScan(ShareScanRequest.builder()
                .reportId(newReport().getId())
                .sharingLevel(SharingLevel.SPECIFIC_COLLEAGUES)
                .colleagueIds(List.of(radiologyColleague.getId()))
                .title("Please review")
                .build(), owner);

        assertThat(sharedWithMe(radiologyColleague)).hasSize(1);
        assertThat(sharedWithMe(cardiologyColleague)).isEmpty();
        assertThat(sharedWithMe(outsider)).isEmpty();
        assertThat(unreadNotifications(radiologyColleague)).isEqualTo(1);
    }

    @Test
    @DisplayName("department share reaches and notifies the whole department")
    void departmentShareNotifiesDepartment() {
        collaborationService.shareScan(ShareScanRequest.builder()
                .reportId(newReport().getId())
                .sharingLevel(SharingLevel.DEPARTMENT)
                .department("Radiology")
                .title("Departmental review")
                .build(), owner);

        assertThat(sharedWithMe(radiologyColleague)).hasSize(1);
        assertThat(sharedWithMe(cardiologyColleague)).isEmpty();

        // The defect this pins down: department shares previously landed silently.
        assertThat(unreadNotifications(radiologyColleague)).isEqualTo(1);
    }

    @Test
    @DisplayName("organization-wide share reaches everyone inside and nobody outside")
    void organizationWideShare() {
        collaborationService.shareScan(ShareScanRequest.builder()
                .reportId(newReport().getId())
                .sharingLevel(SharingLevel.EVERYONE)
                .title("Everyone please look")
                .build(), owner);

        assertThat(sharedWithMe(radiologyColleague)).hasSize(1);
        assertThat(sharedWithMe(cardiologyColleague)).hasSize(1);
        assertThat(sharedWithMe(outsider)).isEmpty();

        assertThat(unreadNotifications(radiologyColleague)).isEqualTo(1);
        assertThat(unreadNotifications(cardiologyColleague)).isEqualTo(1);
    }

    @Test
    @DisplayName("pagination reports the true total and never repeats or drops a scan")
    void paginationIsConsistent() {
        for (int i = 0; i < 25; i++) {
            collaborationService.shareScan(ShareScanRequest.builder()
                    .reportId(newReport().getId())
                    .sharingLevel(SharingLevel.EVERYONE)
                    .title("Case " + i)
                    .build(), owner);
        }

        Page<SharedScanResponse> firstPage = collaborationService.getScansSharedWithMe(
                radiologyColleague, PageRequest.of(0, 10));

        assertThat(firstPage.getTotalElements()).isEqualTo(25);
        assertThat(firstPage.getTotalPages()).isEqualTo(3);
        assertThat(firstPage.getContent()).hasSize(10);

        List<String> seen = new java.util.ArrayList<>();
        for (int page = 0; page < firstPage.getTotalPages(); page++) {
            collaborationService.getScansSharedWithMe(radiologyColleague, PageRequest.of(page, 10))
                    .forEach(scan -> seen.add(scan.getId()));
        }

        assertThat(seen).hasSize(25);
        assertThat(seen).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("a department share without a department is rejected with a usable message")
    void departmentShareRequiresADepartment() {
        assertThatThrownBy(() -> collaborationService.shareScan(ShareScanRequest.builder()
                .reportId(newReport().getId())
                .sharingLevel(SharingLevel.DEPARTMENT)
                .title("Missing department")
                .build(), owner))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("department is required");
    }

    @Test
    @DisplayName("colleague picker lists active org members and excludes the caller")
    void colleaguePickerExcludesSelfAndOtherTenants() {
        List<String> emails = collaborationService
                .getShareableColleagues(owner, null, PageRequest.of(0, 50))
                .map(colleague -> colleague.getEmail())
                .getContent();

        assertThat(emails).contains(radiologyColleague.getEmail(), cardiologyColleague.getEmail());
        assertThat(emails).doesNotContain(owner.getEmail(), outsider.getEmail());
    }

    // ------------------------------------------------------------------ helpers

    private List<SharedScanResponse> sharedWithMe(User user) {
        return collaborationService.getScansSharedWithMe(user, PageRequest.of(0, 50)).getContent();
    }

    private long unreadNotifications(User user) {
        return notificationRepository.countByRecipientIdAndOrganizationIdAndIsReadFalse(
                user.getId(), user.getOrganizationId());
    }

    private Report newReport() {
        return reportRepository.save(Report.builder()
                .organization(organization)
                .user(owner)
                .patientName("Test Patient")
                .patientAge(40)
                .scanDate(LocalDate.now())
                .scanType(ScanType.ABDOMINAL)
                .findings("Unremarkable")
                .status("DRAFT")
                .build());
    }

    private User user(Organization org, String handle, Role role, String department) {
        return User.builder()
                .organization(org)
                .email(handle + "@test.local")
                .passwordHash("hash")
                .firstName("Test")
                .lastName(handle)
                .role(role)
                .department(department)
                .emailVerified(true)
                .accountLocked(false)
                .active(true)
                .build();
    }
}
