package com.giftedlabs.echoinhealthbackend.performance;

import com.giftedlabs.echoinhealthbackend.dto.vault.SearchTemplatesRequest;
import com.giftedlabs.echoinhealthbackend.entity.Organization;
import com.giftedlabs.echoinhealthbackend.entity.Role;
import com.giftedlabs.echoinhealthbackend.entity.ScanType;
import com.giftedlabs.echoinhealthbackend.entity.SubscriptionTier;
import com.giftedlabs.echoinhealthbackend.entity.User;
import com.giftedlabs.echoinhealthbackend.repository.OrganizationRepository;
import com.giftedlabs.echoinhealthbackend.repository.UserRepository;
import com.giftedlabs.echoinhealthbackend.service.TemplateService;
import com.giftedlabs.echoinhealthbackend.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Measures the Phase 12 performance targets that were still open:
 *
 * <ul>
 *   <li><b>UR-076</b> — vault search under 2s with 5,000 templates.</li>
 *   <li><b>UR-077</b> — template load under 1s.</li>
 *   <li><b>UR-078</b> — tier concurrency (5 / 15 / 25 users) without degradation.</li>
 * </ul>
 *
 * <p>These could not be measured before: search loaded every visible template, mapped each to a
 * DTO and filtered in Java, so cost scaled with library size rather than page size. It now runs
 * as one indexed query, which is what makes the target reachable — and measurable.
 *
 * <p>Timings are printed so regressions are visible in build logs. Assertions are set at the
 * requirement thresholds, which leaves generous headroom over observed values; the printed
 * numbers are the useful signal.
 */
@Tag("performance")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VaultSearchPerformancePostgresTest extends PostgresIntegrationTest {

    private static final int TEMPLATE_COUNT = 5_000;
    private static final int PAGE_SIZE = 20;

    private static final ScanType[] SCAN_TYPES = {
            ScanType.ABDOMINAL, ScanType.THYROID, ScanType.OBSTETRIC_EARLY,
            ScanType.BREAST, ScanType.SCROTAL, ScanType.ECHO_ADULT,
            ScanType.ARTERIAL_DOPPLER_BOTH_LOWER, ScanType.PELVIC_FEMALE };
    private static final String[] CATEGORIES = {
            "Abdominal & Pelvic", "Small Parts", "Obstetric", "Cardiac", "Vascular/Doppler" };

    private static boolean seeded;
    private static String userId;
    private static String sampleTemplateId;

    @Autowired private TemplateService templateService;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void announce() {
        System.out.printf("%n=== Vault search performance: %,d templates ===%n", TEMPLATE_COUNT);
    }

    private void seedOnce() {
        if (seeded) {
            return;
        }
        Organization organization = organizationRepository.save(Organization.builder()
                .name("Perf Org")
                .hospitalName("Perf Hospital")
                .subscriptionTier(SubscriptionTier.ULTIMATE)
                .build());
        User user = userRepository.save(User.builder()
                .email("perf-" + UUID.randomUUID() + "@test.local")
                .passwordHash("x").firstName("Perf").lastName("User")
                .role(Role.SONOGRAPHER).organization(organization).build());
        userId = user.getId();

        List<Object[]> batch = new ArrayList<>(TEMPLATE_COUNT);
        for (int i = 0; i < TEMPLATE_COUNT; i++) {
            String id = UUID.randomUUID().toString();
            if (i == 0) {
                sampleTemplateId = id;
            }
            ScanType scanType = SCAN_TYPES[i % SCAN_TYPES.length];
            batch.add(new Object[] {
                    id, organization.getId(), userId,
                    "Protocol " + i + " " + scanType.name().toLowerCase().replace('_', ' '),
                    "Standard reporting template for " + scanType.name().toLowerCase().replace('_', ' ')
                            + ". Includes measurements and impression guidance. Serial " + i + ".",
                    scanType.name(),
                    CATEGORIES[i % CATEGORIES.length],
                    // Deliberately skewed: one common tag, one rare, so filters differ in selectivity.
                    i % 3 == 0 ? "{routine,normal}" : (i % 97 == 0 ? "{rare,complex}" : "{routine}"),
                    i % 50 == 0 // a realistic minority are favourites
            });
        }

        jdbcTemplate.batchUpdate("""
                INSERT INTO report_templates
                    (id, organization_id, user_id, name, description, scan_type, category, tags,
                     is_favorite, is_active, is_default, phi_free, blob_deleted, usage_count,
                     created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::text[], ?, true, false, true, false, 0,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, batch);

        jdbcTemplate.execute("ANALYZE report_templates");
        seeded = true;
    }

    // ------------------------------------------------------------------ UR-076

    @Test
    @Order(1)
    void ur076_searchStaysUnderTwoSecondsAtFiveThousandTemplates() {
        seedOnce();
        assertEquals(TEMPLATE_COUNT, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM report_templates WHERE user_id = ?", Integer.class, userId));

        record Scenario(String name, SearchTemplatesRequest request) {}
        List<Scenario> scenarios = List.of(
                new Scenario("unfiltered (first page)", SearchTemplatesRequest.builder().build()),
                new Scenario("keyword", SearchTemplatesRequest.builder().keyword("impression").build()),
                new Scenario("keyword (rare term)", SearchTemplatesRequest.builder().keyword("Serial 4321").build()),
                new Scenario("scan type", SearchTemplatesRequest.builder().scanType(ScanType.THYROID).build()),
                new Scenario("category", SearchTemplatesRequest.builder().category("Obstetric").build()),
                new Scenario("tag (common)", SearchTemplatesRequest.builder().tag("routine").build()),
                new Scenario("tag (rare)", SearchTemplatesRequest.builder().tag("rare").build()),
                new Scenario("favourites only", SearchTemplatesRequest.builder().favoritesOnly(true).build()),
                new Scenario("sorted by name", SearchTemplatesRequest.builder()
                        .sortBy("name").sortDirection("asc").build()));

        System.out.printf("%n%-26s %8s %8s %8s   %s%n", "scenario", "p50", "p95", "max", "matches");
        for (Scenario scenario : scenarios) {
            Stats stats = measure(30, () ->
                    templateService.searchTemplates(scenario.request(), userId, PageRequest.of(0, PAGE_SIZE)));
            long matches = templateService.searchTemplates(
                    scenario.request(), userId, PageRequest.of(0, PAGE_SIZE)).getTotalElements();

            System.out.printf("%-26s %6dms %6dms %6dms   %,d%n",
                    scenario.name(), stats.p50(), stats.p95(), stats.max(), matches);

            assertTrue(stats.p95() < 2_000,
                    "UR-076: '" + scenario.name() + "' p95 was " + stats.p95() + "ms, target <2000ms");
        }
    }

    /** Deep paging is where an in-memory implementation degrades worst. */
    @Test
    @Order(2)
    void ur076_deepPagingDoesNotDegrade() {
        seedOnce();
        SearchTemplatesRequest request = SearchTemplatesRequest.builder()
                .sortBy("name").sortDirection("asc").build();

        Stats firstPage = measure(20, () ->
                templateService.searchTemplates(request, userId, PageRequest.of(0, PAGE_SIZE)));
        Stats lastPage = measure(20, () ->
                templateService.searchTemplates(request, userId,
                        PageRequest.of(TEMPLATE_COUNT / PAGE_SIZE - 1, PAGE_SIZE)));

        System.out.printf("%npaging: first page p95 %dms, last page p95 %dms%n",
                firstPage.p95(), lastPage.p95());

        assertTrue(lastPage.p95() < 2_000,
                "UR-076: last page p95 was " + lastPage.p95() + "ms, target <2000ms");
    }

    // ------------------------------------------------------------------ UR-077

    @Test
    @Order(3)
    void ur077_singleTemplateLoadsUnderOneSecond() {
        seedOnce();
        Stats stats = measure(50, () -> templateService.getAccessibleTemplate(sampleTemplateId, userId));

        System.out.printf("%ntemplate load: p50 %dms, p95 %dms, max %dms%n",
                stats.p50(), stats.p95(), stats.max());

        assertTrue(stats.p95() < 1_000,
                "UR-077: template load p95 was " + stats.p95() + "ms, target <1000ms");
    }

    // ------------------------------------------------------------------ UR-078

    @Test
    @Order(4)
    void ur078_concurrentUsersPerTierDoNotDegrade() throws Exception {
        seedOnce();

        // Establish the single-user baseline the concurrent runs are compared against.
        Stats baseline = measure(20, () -> templateService.searchTemplates(
                SearchTemplatesRequest.builder().keyword("impression").build(),
                userId, PageRequest.of(0, PAGE_SIZE)));
        System.out.printf("%nbaseline (1 user): p50 %dms, p95 %dms%n", baseline.p50(), baseline.p95());

        System.out.printf("%n%-22s %8s %8s %8s %8s%n", "concurrency", "p50", "p95", "max", "errors");
        for (int users : new int[] { 5, 15, 25 }) { // Basic / Pro / Ultimate
            ConcurrentResult result = runConcurrent(users, 8);

            System.out.printf("%-22s %6dms %6dms %6dms %8d%n",
                    users + " users (" + tierFor(users) + ")",
                    result.stats().p50(), result.stats().p95(), result.stats().max(), result.errors());

            assertEquals(0, result.errors(),
                    "UR-078: " + users + " concurrent users produced " + result.errors() + " errors");
            assertTrue(result.stats().p95() < 2_000,
                    "UR-078: at " + users + " users p95 was " + result.stats().p95() + "ms, target <2000ms");
        }
    }

    private String tierFor(int users) {
        return switch (users) {
            case 5 -> "Basic";
            case 15 -> "Pro";
            default -> "Ultimate";
        };
    }

    private ConcurrentResult runConcurrent(int users, int requestsEach) throws Exception {
        CountDownLatch startGun = new CountDownLatch(1);
        List<Callable<List<Long>>> tasks = new ArrayList<>();

        for (int u = 0; u < users; u++) {
            final int seed = u;
            tasks.add(() -> {
                startGun.await();
                List<Long> timings = new ArrayList<>();
                for (int r = 0; r < requestsEach; r++) {
                    // Vary the query per user so results are not served from one warm plan.
                    SearchTemplatesRequest request = SearchTemplatesRequest.builder()
                            .keyword("Protocol " + ((seed * 137 + r * 31) % TEMPLATE_COUNT))
                            .scanType(SCAN_TYPES[(seed + r) % SCAN_TYPES.length])
                            .build();
                    long startedAt = System.nanoTime();
                    templateService.searchTemplates(request, userId, PageRequest.of(0, PAGE_SIZE));
                    timings.add((System.nanoTime() - startedAt) / 1_000_000);
                }
                return timings;
            });
        }

        ExecutorService pool = Executors.newFixedThreadPool(users);
        List<Long> allTimings = new ArrayList<>();
        int errors = 0;
        try {
            List<Future<List<Long>>> futures = new ArrayList<>();
            for (Callable<List<Long>> task : tasks) {
                futures.add(pool.submit(task));
            }
            startGun.countDown();
            for (Future<List<Long>> future : futures) {
                try {
                    allTimings.addAll(future.get());
                } catch (Exception e) {
                    errors++;
                }
            }
        } finally {
            pool.shutdownNow();
        }
        return new ConcurrentResult(Stats.of(allTimings), errors);
    }

    // ------------------------------------------------------------------ helpers

    private record ConcurrentResult(Stats stats, int errors) {}

    private record Stats(long p50, long p95, long max) {
        static Stats of(List<Long> samples) {
            List<Long> sorted = new ArrayList<>(samples);
            sorted.sort(Long::compareTo);
            if (sorted.isEmpty()) {
                return new Stats(0, 0, 0);
            }
            return new Stats(
                    sorted.get((int) (sorted.size() * 0.50)),
                    sorted.get(Math.min(sorted.size() - 1, (int) (sorted.size() * 0.95))),
                    sorted.get(sorted.size() - 1));
        }
    }

    private Stats measure(int iterations, Supplier<Object> operation) {
        for (int i = 0; i < 5; i++) {
            operation.get(); // warm the plan cache and JIT before timing
        }
        List<Long> timings = new ArrayList<>(iterations);
        for (int i = 0; i < iterations; i++) {
            long startedAt = System.nanoTime();
            Object result = operation.get();
            timings.add((System.nanoTime() - startedAt) / 1_000_000);
            if (result instanceof Page<?> page) {
                assertTrue(page.getSize() <= PAGE_SIZE, "page size must be respected");
            }
        }
        return Stats.of(timings);
    }
}
