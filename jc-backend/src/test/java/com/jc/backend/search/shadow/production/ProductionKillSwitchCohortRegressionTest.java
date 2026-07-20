package com.jc.backend.search.shadow.production;

import static org.assertj.core.api.Assertions.assertThat;

import com.jc.intelligence.production.search.v1.AllowlistedInternalCohortSelector;
import com.jc.intelligence.production.search.v1.DisabledSearchShadowKillSwitch;
import com.jc.intelligence.production.search.v1.EmptyProductionShadowCohortSelector;
import com.jc.intelligence.production.search.v1.ProductionShadowDispatchRequestV1;
import com.jc.intelligence.production.search.v1.ProductionShadowDispatchStatus;
import com.jc.intelligence.production.search.v1.ProductionShadowResourcePolicyV1;
import com.jc.intelligence.production.search.v1.ProductionShadowSamplingAuthorization;
import com.jc.intelligence.production.search.v1.ProductionShadowTaskExecutor;
import com.jc.intelligence.production.search.v1.ProductionShadowTechnicalGate;
import com.jc.intelligence.production.search.v1.PropertyBackedSearchShadowKillSwitch;
import com.jc.intelligence.production.search.v1.InMemorySearchShadowMetricSink;
import com.jc.intelligence.production.search.v1.TestMutableSearchShadowKillSwitch;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ProductionKillSwitchCohortRegressionTest {
    private static final String INTERNAL_HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void defaultsAreKilledEmptyAndZeroSample() {
        assertThat(new DisabledSearchShadowKillSwitch().killed()).isTrue();
        assertThat(new EmptyProductionShadowCohortSelector().includes(INTERNAL_HASH)).isFalse();
        assertThat(ProductionShadowSamplingAuthorization.productionDefault().effectiveBasisPoints()).isZero();
        assertThat(new PropertyBackedSearchShadowKillSwitch(() -> null, () -> true).killed()).isTrue();
        assertThat(new PropertyBackedSearchShadowKillSwitch(() -> "garbage", () -> true).killed()).isTrue();
    }

    @Test
    void emergencyKillStopsAllSubsequentDispatchAndPreservesLegacyIdentity() throws Exception {
        var killSwitch = new TestMutableSearchShadowKillSwitch();
        killSwitch.enable();
        var cohort = new AllowlistedInternalCohortSelector(Set.of(INTERNAL_HASH), true);
        var metrics = new InMemorySearchShadowMetricSink();
        var taskCalls = new AtomicInteger();
        Object legacy = new Object();
        try (var executor = new ProductionShadowTaskExecutor(ProductionShadowResourcePolicyV1.provisional())) {
            var gate = new ProductionShadowTechnicalGate<Object>(killSwitch, cohort,
                    ProductionShadowSamplingAuthorization.technicalTestOnly(10_000), executor, metrics);
            var first = gate.dispatch(new ProductionShadowDispatchRequestV1<>(legacy, "stable-key",
                    INTERNAL_HASH, taskCalls::incrementAndGet));
            assertThat(first.status()).isEqualTo(ProductionShadowDispatchStatus.SUBMITTED);
            assertThat(first.legacyResponse()).isSameAs(legacy);
            for (int i = 0; i < 100 && taskCalls.get() == 0; i++) Thread.sleep(5);
            assertThat(taskCalls.get()).isEqualTo(1);

            killSwitch.kill();
            var killed = gate.dispatch(new ProductionShadowDispatchRequestV1<>(legacy, "stable-key-2",
                    INTERNAL_HASH, taskCalls::incrementAndGet));
            assertThat(killed.status()).isEqualTo(ProductionShadowDispatchStatus.KILLED);
            assertThat(killed.legacyResponse()).isSameAs(legacy);
            Thread.sleep(20);
            assertThat(taskCalls.get()).isEqualTo(1);
        }
    }
}
