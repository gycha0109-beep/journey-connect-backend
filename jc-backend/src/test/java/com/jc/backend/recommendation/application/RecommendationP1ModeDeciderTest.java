package com.jc.backend.recommendation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jc.backend.recommendation.config.RecommendationP1Properties;
import com.jc.backend.recommendation.config.RecommendationProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("p1-verification")
class RecommendationP1ModeDeciderTest {

    @Test
    void offIsSafeAndNeverAllocates() {
        RecommendationProperties base = new RecommendationProperties();
        RecommendationP1Properties p1 = new RecommendationP1Properties();
        RecommendationP1ModeDecider decider = new RecommendationP1ModeDecider(base, p1);

        decider.afterSingletonsInstantiated();

        assertThat(decider.shouldRunShadow()).isFalse();
        assertThat(decider.shouldServeCanary(42L)).isFalse();
    }

    @Test
    void shadowRequiresMatchingBaseMode() {
        RecommendationProperties base = new RecommendationProperties();
        RecommendationP1Properties p1 = enabled(RecommendationP1Properties.Mode.SHADOW);
        RecommendationP1ModeDecider decider = new RecommendationP1ModeDecider(base, p1);

        assertThatThrownBy(decider::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("base recommendation SHADOW");

        base.setMode(RecommendationProperties.Mode.SHADOW);
        decider.afterSingletonsInstantiated();
        assertThat(decider.shouldRunShadow()).isTrue();
    }

    @Test
    void canaryAllocationIsDeterministicReleaseBoundAndRollbackable() {
        RecommendationProperties base = new RecommendationProperties();
        base.setMode(RecommendationProperties.Mode.CANARY);
        RecommendationP1Properties p1 = enabled(RecommendationP1Properties.Mode.CANARY);
        p1.setCanaryAllocationBasisPoints(5_000);
        RecommendationP1ModeDecider decider = new RecommendationP1ModeDecider(base, p1);
        decider.afterSingletonsInstantiated();

        boolean first = decider.shouldServeCanary(123456L);
        assertThat(decider.shouldServeCanary(123456L)).isEqualTo(first);

        p1.setMode(RecommendationP1Properties.Mode.OFF);
        assertThat(decider.shouldServeCanary(123456L)).isFalse();
    }

    @Test
    void canaryRejectsZeroAllocation() {
        RecommendationProperties base = new RecommendationProperties();
        base.setMode(RecommendationProperties.Mode.CANARY);
        RecommendationP1Properties p1 = enabled(RecommendationP1Properties.Mode.CANARY);
        p1.setCanaryAllocationBasisPoints(0);
        RecommendationP1ModeDecider decider = new RecommendationP1ModeDecider(base, p1);

        assertThatThrownBy(decider::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nonzero allocation");
    }

    private static RecommendationP1Properties enabled(RecommendationP1Properties.Mode mode) {
        RecommendationP1Properties properties = new RecommendationP1Properties();
        properties.setMode(mode);
        properties.setReleaseId("p1-test-release");
        return properties;
    }
}
