package com.jc.backend.recommendation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jc.backend.recommendation.config.RecommendationProperties;
import com.jc.backend.recommendation.config.RecommendationProperties.Mode;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecommendationShadowServiceTest {

    @Test
    void orchestrationFailureIsContainedAndLegacyPathCanContinue() {
        RecommendationProperties properties = properties(Mode.SHADOW);
        RecommendationModeDecider decider = new RecommendationModeDecider(properties);
        RecommendationOrchestrationService orchestration = mock(RecommendationOrchestrationService.class);
        RecommendationReplayService replay = mock(RecommendationReplayService.class);
        when(orchestration.runShadow(any())).thenThrow(new IllegalStateException("test failure"));
        RecommendationShadowService service = new RecommendationShadowService(
                properties, decider, orchestration, replay);

        RecommendationShadowService.ShadowOutcome outcome =
                service.observeHomeFeed(10L, "jwt-shadow-fail-open", true);

        assertThat(outcome.status())
                .isEqualTo(RecommendationShadowService.ShadowOutcome.Status.FAILED);
        assertThat(outcome.runId()).isNull();
        verify(replay, never()).audit(any());
    }

    @Test
    void nonExactPersistenceReplayIsContained() {
        RecommendationProperties properties = properties(Mode.SHADOW);
        RecommendationModeDecider decider = new RecommendationModeDecider(properties);
        RecommendationOrchestrationService orchestration = mock(RecommendationOrchestrationService.class);
        RecommendationReplayService replay = mock(RecommendationReplayService.class);
        when(orchestration.runShadow(any())).thenReturn(
                new RecommendationOrchestrationService.RunResult("run:1", "request:1", 1, 1, 0, 1));
        when(replay.audit("run:1")).thenReturn(new RecommendationReplayService.ReplayAuditResult(
                "replay:1", "run:1", false, "mismatch", List.of("result_snapshot"), "a".repeat(64)));
        RecommendationShadowService service = new RecommendationShadowService(
                properties, decider, orchestration, replay);

        var outcome = service.observeHomeFeed(10L, "jwt-shadow-replay", true);

        assertThat(outcome.status())
                .isEqualTo(RecommendationShadowService.ShadowOutcome.Status.FAILED);
    }

    @Test
    void skippedModeDoesNotInvokeOrchestrationOrReplay() {
        RecommendationProperties properties = properties(Mode.OFF);
        RecommendationModeDecider decider = new RecommendationModeDecider(properties);
        RecommendationOrchestrationService orchestration = mock(RecommendationOrchestrationService.class);
        RecommendationReplayService replay = mock(RecommendationReplayService.class);
        RecommendationShadowService service = new RecommendationShadowService(
                properties, decider, orchestration, replay);

        RecommendationShadowService.ShadowOutcome outcome =
                service.observeHomeFeed(10L, "jwt-shadow-off", true);

        assertThat(outcome.status())
                .isEqualTo(RecommendationShadowService.ShadowOutcome.Status.SKIPPED);
        verify(orchestration, never()).runShadow(any());
        verify(replay, never()).audit(any());
    }

    private static RecommendationProperties properties(Mode mode) {
        RecommendationProperties properties = new RecommendationProperties();
        properties.setMode(mode);
        return properties;
    }
}
