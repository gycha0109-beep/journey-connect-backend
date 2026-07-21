package com.jc.backend.search.shadow.production;

import static org.assertj.core.api.Assertions.assertThat;

import com.jc.intelligence.production.search.v1.NoOpSearchShadowMetricSink;
import com.jc.intelligence.production.search.v1.ProductionShadowResourcePolicyV1;
import com.jc.intelligence.production.search.v1.ProductionShadowTaskExecutor;
import com.jc.intelligence.production.search.v1.TestMutableSearchShadowKillSwitch;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ProductionSearchShadowPilotReadinessTest {
    private static final Instant INSIDE = Instant.parse("2026-07-21T10:30:00Z");
    private static final Instant OUTSIDE = Instant.parse("2026-07-21T12:00:00Z");

    @Test
    void validOperationalInputsStillRequireWindowCohortAndDeterministicSample() throws Exception {
        var config = ProductionSearchShadowPropertiesValidator.validate(
                ProductionSearchShadowPropertiesTest.validEnabled());
        var switchControl = new TestMutableSearchShadowKillSwitch();
        switchControl.enable();
        var work = new AtomicInteger();
        try (var executor = new ProductionShadowTaskExecutor(
                ProductionShadowResourcePolicyV1.approvedInitialPilot())) {
            var gate = new ProductionSearchShadowOperationalGate(
                    config,
                    switchControl,
                    key -> true,
                    new ProductionSearchShadowSamplingGate(10),
                    executor,
                    new NoOpSearchShadowMetricSink(),
                    Clock.fixed(INSIDE, ZoneOffset.UTC));
            var decision = gate.dispatch(Optional.of(config.allowlistHashes().iterator().next()),
                    work::incrementAndGet, ignored -> { });
            assertThat(decision.reason()).isIn(
                    ProductionSearchShadowActivationReason.DISPATCHED,
                    ProductionSearchShadowActivationReason.NOT_SAMPLED);
        }
    }

    @Test
    void closedActivationWindowBlocksBeforeIdentityResolutionOrExecutorSubmission() throws Exception {
        var config = ProductionSearchShadowPropertiesValidator.validate(
                ProductionSearchShadowPropertiesTest.validEnabled());
        var identityCalls = new AtomicInteger();
        var workCalls = new AtomicInteger();
        var switchControl = new TestMutableSearchShadowKillSwitch();
        switchControl.enable();
        try (var executor = new ProductionShadowTaskExecutor(
                ProductionShadowResourcePolicyV1.approvedInitialPilot())) {
            var gate = new ProductionSearchShadowOperationalGate(
                    config,
                    switchControl,
                    key -> true,
                    new ProductionSearchShadowSamplingGate(10),
                    executor,
                    new NoOpSearchShadowMetricSink(),
                    Clock.fixed(OUTSIDE, ZoneOffset.UTC));
            var decision = gate.dispatch(() -> {
                identityCalls.incrementAndGet();
                return Optional.of(config.allowlistHashes().iterator().next());
            }, workCalls::incrementAndGet, ignored -> { });
            assertThat(decision.reason()).isEqualTo(
                    ProductionSearchShadowActivationReason.ACTIVATION_WINDOW_CLOSED);
            assertThat(identityCalls).hasValue(0);
            assertThat(workCalls).hasValue(0);
        }
    }
}
