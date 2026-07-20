package com.jc.backend.recommendation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jc.backend.recommendation.config.RecommendationProperties;
import com.jc.backend.recommendation.config.RecommendationProperties.Mode;
import org.junit.jupiter.api.Test;

class RecommendationModeDeciderTest {

    @Test
    void shadowRunsOnlyForAuthenticatedFirstPage() {
        RecommendationModeDecider decider = new RecommendationModeDecider(properties(Mode.SHADOW));
        decider.afterSingletonsInstantiated();

        assertThat(decider.shouldRunHomeShadow(10L, true)).isTrue();
        assertThat(decider.shouldRunHomeShadow(10L, false)).isFalse();
        assertThat(decider.shouldRunHomeShadow(null, true)).isFalse();
        assertThat(decider.shouldRunHomeShadow(0L, true)).isFalse();
    }

    @Test
    void canaryRequiresExplicitReleaseControlsAndUsesStableAllocation() {
        RecommendationProperties properties = properties(Mode.CANARY);
        properties.setCanaryAllocationBasisPoints(10000);
        properties.setCanaryCursorSecret("0123456789abcdef0123456789abcdef");
        properties.setCanaryReleaseId("release:p0-6");
        RecommendationModeDecider decider = new RecommendationModeDecider(properties);
        decider.afterSingletonsInstantiated();

        assertThat(decider.shouldServeHomeCanary(10L)).isTrue();
        assertThat(decider.shouldServeHomeCanary(10L)).isTrue();
        assertThat(decider.shouldServeHomeCanary(null)).isFalse();
        assertThat(decider.isCanaryMode()).isTrue();
    }


    @Test
    void canaryAllocationIsStableWithinAndNamespacedByRelease() {
        RecommendationProperties releaseA = properties(Mode.CANARY);
        releaseA.setCanaryAllocationBasisPoints(5000);
        releaseA.setCanaryCursorSecret("0123456789abcdef0123456789abcdef");
        releaseA.setCanaryReleaseId("release:a");
        RecommendationProperties releaseB = properties(Mode.CANARY);
        releaseB.setCanaryAllocationBasisPoints(5000);
        releaseB.setCanaryCursorSecret("0123456789abcdef0123456789abcdef");
        releaseB.setCanaryReleaseId("release:b");
        RecommendationModeDecider first = new RecommendationModeDecider(releaseA);
        RecommendationModeDecider second = new RecommendationModeDecider(releaseB);
        first.afterSingletonsInstantiated();
        second.afterSingletonsInstantiated();

        assertThat(java.util.stream.LongStream.rangeClosed(1, 1000)
                .anyMatch(userId -> first.shouldServeHomeCanary(userId)
                        != second.shouldServeHomeCanary(userId)))
                .isTrue();
        assertThat(first.shouldServeHomeCanary(42L))
                .isEqualTo(first.shouldServeHomeCanary(42L));
    }

    @Test
    void liveAndIncompleteCanaryFailClosed() {
        RecommendationModeDecider live = new RecommendationModeDecider(properties(Mode.LIVE));
        assertThatThrownBy(live::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LIVE");

        RecommendationModeDecider canary = new RecommendationModeDecider(properties(Mode.CANARY));
        assertThatThrownBy(canary::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("allocation");
    }

    private static RecommendationProperties properties(Mode mode) {
        RecommendationProperties properties = new RecommendationProperties();
        properties.setMode(mode);
        return properties;
    }
}
