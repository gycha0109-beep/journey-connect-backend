package com.jc.recommendation.model.exploration;

import com.jc.recommendation.model.entity.RecommendationEntityType;

import java.util.Objects;

public record ExplorationCandidateMetadata(
        String entityId,
        RecommendationEntityType entityType,
        String authorId,
        String primaryRegionFeatureId,
        String primaryThemeFeatureId,
        String duplicateGroupId,
        int recentExposureCount
) {
    public ExplorationCandidateMetadata {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(entityType, "entityType");
    }
}
