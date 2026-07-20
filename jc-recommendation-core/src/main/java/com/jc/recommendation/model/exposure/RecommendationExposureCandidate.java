package com.jc.recommendation.model.exposure;

import com.jc.recommendation.model.entity.RecommendationEntityType;
import java.util.Objects;

public record RecommendationExposureCandidate(
        String entityId, RecommendationEntityType entityType, int absoluteRank, int pagePosition,
        RecommendationExposureOrigin origin, Double score, boolean scoreIsNegativeZero,
        Integer baseAbsoluteRank, Integer diversifiedAbsoluteRank, Double explorationQualityScore,
        Integer recentExposureCount, Long seededTieBreakKey, Integer explorationPoolRank, Integer targetInsertionRank
) {
    public RecommendationExposureCandidate {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(origin, "origin");
    }
}
