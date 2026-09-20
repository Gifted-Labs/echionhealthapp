package com.giftedlabs.echoinhealthbackend.service;

import com.giftedlabs.echoinhealthbackend.dto.vault.ReportResponse;
import com.giftedlabs.echoinhealthbackend.dto.vault.SearchReportsRequest;
import com.giftedlabs.echoinhealthbackend.entity.*;
import com.giftedlabs.echoinhealthbackend.repository.OrganizationRepository;
import com.giftedlabs.echoinhealthbackend.repository.ReportRepository;
import com.giftedlabs.echoinhealthbackend.repository.UserRepository;
import com.giftedlabs.echoinhealthbackend.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Report listing must return the same rows in the same order for the same request.
 *
 * <p>The client reported that opening a freshly uploaded report showed a different one. The search
 * query's last sort key was {@code created_at}, and a batch upload commits many rows inside one
 * transaction with effectively identical timestamps — leaving PostgreSQL free to return tied rows
 * in a different order on each call. A list that reshuffles under the user is indistinguishable,
 * from the UI, from opening the wrong report. The query now ends on a unique id tiebreak.
 *
 * <p>These rows are deliberately given an identical scan date so the earlier sort keys tie and the
 * tiebreak is what is actually under test.
 */
class ReportOrderingPostgresTest extends PostgresIntegrationTest {

    private static final int REPORT_COUNT = 25;

    @Autowired
    private ReportService reportService;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ReportRepository reportRepository;

    private User author;

    @BeforeEach
    void seed() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        Organization organization = organizationRepository.save(Organization.builder()
                .name("Ordering Org " + suffix)
                .hospitalName("Ordering Hospital")
                .subscriptionTier(SubscriptionTier.PRO)
                .build());

        author = userRepository.save(User.builder()
                .organization(organization)
                .email("author-" + suffix + "@test.local")
                .passwordHash("hash")
                .firstName("Test")
                .lastName("Author")
                .role(Role.SONOGRAPHER)
                .emailVerified(true)
                .accountLocked(false)
                .active(true)
                .build());

        LocalDate sameDay = LocalDate.now();
        for (int i = 0; i < REPORT_COUNT; i++) {
            reportRepository.save(Report.builder()
                    .organization(organization)
                    .user(author)
                    .patientName("Patient " + i)
                    .patientAge(30 + i)
                    .scanDate(sameDay)
                    .scanType(ScanType.ABDOMINAL)
                    .findings("Findings " + i)
                    .status("DRAFT")
                    .build());
        }
    }

    @Test
    @DisplayName("the same search returns the same order every time")
    void repeatedSearchesAgree() {
        List<String> first = idsOnPage(0, REPORT_COUNT);

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(idsOnPage(0, REPORT_COUNT))
                    .as("ordering changed between identical requests on attempt %d", attempt)
                    .containsExactlyElementsOf(first);
        }
    }

    @Test
    @DisplayName("paging visits every report exactly once")
    void pagingIsCompleteAndWithoutDuplicates() {
        List<String> seen = new ArrayList<>();
        int pageSize = 10;
        int pages = (REPORT_COUNT + pageSize - 1) / pageSize;

        for (int page = 0; page < pages; page++) {
            seen.addAll(idsOnPage(page, pageSize));
        }

        assertThat(seen).hasSize(REPORT_COUNT);
        assertThat(seen).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("a report fetched by id is the one the list showed at that position")
    void fetchingByIdReturnsTheSameReport() {
        List<ReportResponse> listed = reportService
                .searchReports(new SearchReportsRequest(), author.getId(), PageRequest.of(0, 5))
                .getContent();

        for (ReportResponse listedReport : listed) {
            ReportResponse fetched = reportService.getReport(listedReport.getId(), author.getId());

            assertThat(fetched.getId()).isEqualTo(listedReport.getId());
            assertThat(fetched.getPatientName()).isEqualTo(listedReport.getPatientName());
        }
    }

    private List<String> idsOnPage(int page, int size) {
        return reportService
                .searchReports(new SearchReportsRequest(), author.getId(), PageRequest.of(page, size))
                .map(ReportResponse::getId)
                .getContent();
    }
}
