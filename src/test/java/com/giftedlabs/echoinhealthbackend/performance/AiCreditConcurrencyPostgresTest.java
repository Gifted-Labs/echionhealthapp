package com.giftedlabs.echoinhealthbackend.performance;

import com.giftedlabs.echoinhealthbackend.entity.Organization;
import com.giftedlabs.echoinhealthbackend.entity.SubscriptionTier;
import com.giftedlabs.echoinhealthbackend.exception.SubscriptionLimitExceededException;
import com.giftedlabs.echoinhealthbackend.repository.OrganizationRepository;
import com.giftedlabs.echoinhealthbackend.service.BillingService;
import com.giftedlabs.echoinhealthbackend.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves AI credit reservation cannot overshoot a tier limit under concurrency.
 *
 * <p>This closes the gap left open in the audit: the fix replaced a read-then-write with a
 * single conditional UPDATE, but nothing demonstrated the property held under parallel load.
 * The old check-then-consume pattern would fail this test — every thread would observe the
 * same headroom and every thread would then increment.
 */
class AiCreditConcurrencyPostgresTest extends PostgresIntegrationTest {

    @Autowired
    private BillingService billingService;
    @Autowired
    private OrganizationRepository organizationRepository;

    @Test
    void concurrentReservationsNeverExceedTheTierLimit() throws Exception {
        int headroom = 5;
        int contenders = 40;

        Organization organization = organizationRepository.save(Organization.builder()
                .name("Credit Race Org")
                .hospitalName("Credit Race Hospital")
                .subscriptionTier(SubscriptionTier.BASIC) // 1,000 credits/month
                .aiCreditsUsedThisMonth(SubscriptionTier.BASIC.getAiCreditsPerMonth() - headroom)
                .aiCreditsLastResetAt(java.time.LocalDateTime.now())
                .build());

        AtomicInteger granted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        CountDownLatch startGun = new CountDownLatch(1);

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < contenders; i++) {
            tasks.add(() -> {
                startGun.await(); // release every thread at the same instant
                try {
                    billingService.reserveAiCredits(organization.getId(), 1);
                    granted.incrementAndGet();
                } catch (SubscriptionLimitExceededException e) {
                    rejected.incrementAndGet();
                }
                return null;
            });
        }

        ExecutorService pool = Executors.newFixedThreadPool(contenders);
        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (Callable<Void> task : tasks) {
                futures.add(pool.submit(task));
            }
            startGun.countDown();
            for (Future<Void> future : futures) {
                future.get();
            }
        } finally {
            pool.shutdownNow();
        }

        int limit = SubscriptionTier.BASIC.getAiCreditsPerMonth();
        int used = organizationRepository.findById(organization.getId())
                .orElseThrow().getAiCreditsUsedThisMonth();

        assertEquals(headroom, granted.get(),
                "exactly the remaining credits should be granted, no more and no fewer");
        assertEquals(contenders - headroom, rejected.get(),
                "every other contender must be told the limit is reached");
        assertEquals(limit, used, "the organization must land exactly on its limit, never past it");
        assertTrue(used <= limit, "usage must never exceed the tier limit");
    }

    /**
     * A failed generation hands its reservation back, so a tenant is not billed for output they
     * never received — and repeated failures do not silently consume the monthly allowance.
     */
    @Test
    void refundsReturnCreditsAndCannotDriveUsageNegative() {
        Organization organization = organizationRepository.save(Organization.builder()
                .name("Refund Org")
                .hospitalName("Refund Hospital")
                .subscriptionTier(SubscriptionTier.PRO)
                .aiCreditsUsedThisMonth(0)
                .aiCreditsLastResetAt(java.time.LocalDateTime.now())
                .build());

        billingService.reserveAiCredits(organization.getId(), 1);
        billingService.reserveAiCredits(organization.getId(), 1);
        assertEquals(2, reload(organization).getAiCreditsUsedThisMonth());

        billingService.refundAiCredits(organization.getId(), 1);
        assertEquals(1, reload(organization).getAiCreditsUsedThisMonth());

        // Over-refunding must clamp at zero rather than wrapping negative and granting free quota.
        billingService.refundAiCredits(organization.getId(), 50);
        assertEquals(0, reload(organization).getAiCreditsUsedThisMonth());
    }

    private Organization reload(Organization organization) {
        return organizationRepository.findById(organization.getId()).orElseThrow();
    }
}
