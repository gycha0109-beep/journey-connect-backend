package com.jc.recommendation.model.diversity;

import com.jc.recommendation.model.entity.RecommendationEntityType;
import java.util.Objects;

public record DiversityCandidateMetadata(
        String entityId, RecommendationEntityType entityType, String authorId,
        String primaryRegionFeatureId, String primaryThemeFeatureId, String duplicateGroupId
) {
    public DiversityCandidateMetadata {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(entityType, "entityType");
    }
}
