package com.jc.recommendation.model.freshness;

import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.policy.FreshnessPolicy;

import java.util.Objects;

public record CalculateFreshnessInput(
        String entityId,
        RecommendationEntityType entityType,
        String referenceTime,
        String freshnessTimestamp,
        FreshnessTimestampSource timestampSource,
        FreshnessPolicy policy
) {
    public CalculateFreshnessInput {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(referenceTime, "referenceTime");
    }

    public CalculateFreshnessInput(
            String entityId,
            RecommendationEntityType entityType,
            String referenceTime,
            String freshnessTimestamp,
            FreshnessTimestampSource timestampSource
    ) {
        this(entityId, entityType, referenceTime, freshnessTimestamp, timestampSource, null);
    }
}
