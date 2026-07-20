package com.jc.recommendation.model.popularity;

import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.policy.PopularityPolicy;

import java.util.Objects;

public record CalculatePopularityInput(
        String entityId,
        RecommendationEntityType entityType,
        String referenceTime,
        PopularityEngagementSnapshot snapshot,
        PopularityPolicy policy
) {
    public CalculatePopularityInput {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(referenceTime, "referenceTime");
    }

    public CalculatePopularityInput(
            String entityId,
            RecommendationEntityType entityType,
            String referenceTime,
            PopularityEngagementSnapshot snapshot
    ) {
        this(entityId, entityType, referenceTime, snapshot, null);
    }
}
