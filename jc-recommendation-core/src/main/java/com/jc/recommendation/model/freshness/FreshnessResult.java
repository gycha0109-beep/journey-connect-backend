package com.jc.recommendation.model.freshness;

import com.jc.recommendation.model.entity.RecommendationEntityType;

import java.util.Objects;

public record FreshnessResult(
        String entityId,
        RecommendationEntityType entityType,
        FreshnessStatus status,
        Double score,
        Long ageMilliseconds,
        Double ageDays,
        Double halfLifeDays,
        String freshnessTimestamp,
        FreshnessTimestampSource timestampSource,
        FreshnessNotApplicableReason notApplicableReason,
        String policyVersion
) {
    public FreshnessResult {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(policyVersion, "policyVersion");
    }
}
