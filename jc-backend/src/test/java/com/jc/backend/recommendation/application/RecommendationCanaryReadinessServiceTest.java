package com.jc.backend.recommendation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jc.backend.recommendation.config.RecommendationProperties;
import com.jc.backend.recommendation.persistence.RecommendationReplayAuditStore;
import com.jc.backend.recommendation.persistence.RecommendationReplayAuditStore.ReadinessEvidence;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecommendationCanaryReadinessServiceTest {

    @Test
    void blocksWhenEvidenceIsIncompleteOrNonExact() {
        RecommendationProperties properties = properties();
        RecommendationReplayAuditStore store = mock(RecommendationReplayAuditStore.class);
        when(store.findRecentShadowEvidence(10, "p0-persistence-replay-v1", "java-core-1.0.0"))
                .thenReturn(List.of(
                        new ReadinessEvidence("run:1", "succeeded", 10, null),
                        new ReadinessEvidence("run:2", "failed", 1000, "mismatch")));

        var result = new RecommendationCanaryReadinessService(properties, store).evaluate();

        assertThat(result.ready()).isFalse();
        assertThat(result.blockers()).containsExactly(
                "insufficient_shadow_runs",
                "missing_replay_audit",
                "non_exact_replay",
                "failed_or_fallback_run",
                "p95_latency_exceeded");
    }

    @Test
    void readyRequiresEnoughExactSuccessfulRunsWithinLatencyBudget() {
        RecommendationProperties properties = properties();
        properties.setReadinessMinimumShadowRuns(2);
        RecommendationReplayAuditStore store = mock(RecommendationReplayAuditStore.class);
        when(store.findRecentShadowEvidence(10, "p0-persistence-replay-v1", "java-core-1.0.0"))
                .thenReturn(List.of(
                        new ReadinessEvidence("run:1", "succeeded", 10, "exact_match"),
                        new ReadinessEvidence("run:2", "succeeded", 20, "exact_match")));

        var result = new RecommendationCanaryReadinessService(properties, store).evaluate();

        assertThat(result.ready()).isTrue();
        assertThat(result.blockers()).isEmpty();
        assertThat(result.p95DurationMs()).isEqualTo(20);
    }

    private static RecommendationProperties properties() {
        RecommendationProperties properties = new RecommendationProperties();
        properties.setReadinessMinimumShadowRuns(3);
        properties.setReadinessLookbackRuns(10);
        properties.setReadinessMaxP95DurationMs(100);
        return properties;
    }
}
