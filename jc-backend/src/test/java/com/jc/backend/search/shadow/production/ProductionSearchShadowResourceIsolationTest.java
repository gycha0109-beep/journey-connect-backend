package com.jc.backend.search.shadow.production;

import static org.assertj.core.api.Assertions.assertThat;

import com.jc.intelligence.production.search.v1.ProductionShadowDispatchStatus;
import com.jc.intelligence.production.search.v1.ProductionShadowResourcePolicyV1;
import com.jc.intelligence.production.search.v1.ProductionShadowTaskCompletionStatus;
import com.jc.intelligence.production.search.v1.ProductionShadowTaskExecutor;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ProductionSearchShadowResourceIsolationTest {
    @Test
    void approvedBudgetIsBoundedAndTimeoutInterruptsWithoutRequestThreadJoin() throws Exception {
        var policy = ProductionShadowResourcePolicyV1.approvedInitialPilot();
        assertThat(policy.coreConcurrency()).isEqualTo(1);
        assertThat(policy.maxConcurrency()).isEqualTo(2);
        assertThat(policy.queueCapacity()).isEqualTo(8);
        assertThat(policy.runtimeTimeout()).isEqualTo(Duration.ofMillis(200));
        assertThat(policy.hardCancellationTimeout()).isEqualTo(Duration.ofMillis(300));
        assertThat(policy.maximumCandidateCount()).isEqualTo(100);
        assertThat(policy.maximumSampleBasisPoints()).isEqualTo(10);

        var completion = new AtomicReference<ProductionShadowTaskCompletionStatus>();
        var started = new CountDownLatch(1);
        try (var executor = new ProductionShadowTaskExecutor(policy)) {
            long requestStarted = System.nanoTime();
            assertThat(executor.submitTimed(() -> {
                started.countDown();
                try {
                    Thread.sleep(5_000);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }, Duration.ofMillis(20), Duration.ofMillis(40), outcome -> completion.set(outcome.status())))
                    .isEqualTo(ProductionShadowDispatchStatus.SUBMITTED);
            long submitMillis = Duration.ofNanos(System.nanoTime() - requestStarted).toMillis();
            assertThat(submitMillis).isLessThan(100L);
            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
            for (int attempt = 0; attempt < 100 && completion.get() == null; attempt++) {
                Thread.sleep(5);
            }
            assertThat(completion.get()).isIn(
                    ProductionShadowTaskCompletionStatus.TIMED_OUT,
                    ProductionShadowTaskCompletionStatus.HARD_TIMEOUT);
            assertThat(executor.maximumConcurrency()).isEqualTo(2);
            assertThat(executor.queueCapacity()).isEqualTo(8);
        }
    }
    @Test
    void disabledConfigurationDoesNotResolveIdentityOrSubmitWork() throws Exception {
        var config = new ProductionSearchShadowRuntimeConfig(
                false, true, 0, 0, java.util.Set.of(),
                null, null, null, null, null, null, null,
                100, 1, 2, 8, Duration.ofMillis(200), Duration.ofMillis(300));
        var identityCalls = new java.util.concurrent.atomic.AtomicInteger();
        var workCalls = new java.util.concurrent.atomic.AtomicInteger();
        try (var executor = new ProductionShadowTaskExecutor(ProductionShadowResourcePolicyV1.approvedInitialPilot())) {
            var gate = new ProductionSearchShadowOperationalGate(
                    config,
                    new com.jc.intelligence.production.search.v1.DisabledSearchShadowKillSwitch(),
                    key -> false,
                    new ProductionSearchShadowSamplingGate(0),
                    executor,
                    new com.jc.intelligence.production.search.v1.NoOpSearchShadowMetricSink(),
                    java.time.Clock.systemUTC());
            var decision = gate.dispatch(() -> {
                identityCalls.incrementAndGet();
                return java.util.Optional.of("0".repeat(64));
            }, workCalls::incrementAndGet, ignored -> { });
            assertThat(decision.reason()).isEqualTo(
                    ProductionSearchShadowActivationReason.DISABLED_BY_CONFIGURATION);
            assertThat(identityCalls).hasValue(0);
            assertThat(workCalls).hasValue(0);
        }
    }

}
