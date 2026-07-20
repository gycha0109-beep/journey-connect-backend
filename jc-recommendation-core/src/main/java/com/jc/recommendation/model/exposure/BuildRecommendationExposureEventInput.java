package com.jc.recommendation.model.exposure;

import com.jc.recommendation.model.event.EventSurface;
import com.jc.recommendation.model.integration.RankCandidatesWithExplorationResult;
import java.util.Objects;

public record BuildRecommendationExposureEventInput(
        String eventId, String idempotencyKey, String recommendationRunId, String sessionId,
        EventSurface surface, String servedAt, RankCandidatesWithExplorationResult rankingResult
) {
    public BuildRecommendationExposureEventInput {
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(rankingResult, "rankingResult");
    }
}
