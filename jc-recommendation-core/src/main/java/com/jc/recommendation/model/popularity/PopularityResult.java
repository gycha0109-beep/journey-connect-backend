package com.jc.recommendation.model.popularity;

import com.jc.recommendation.model.entity.RecommendationEntityType;

import java.util.Objects;

public record PopularityResult(
        String entityId,
        RecommendationEntityType entityType,
        String snapshotId,
        PopularityStatus status,
        Double score,
        Double qualityScore,
        Double volumeEvidence,
        Double evidenceMultiplier,
        Double likeLowerBound,
        Double saveLowerBound,
        Double shareLowerBound,
        Long uniqueExposureCount,
        PopularityNotApplicableReason notApplicableReason,
        String policyVersion
) {
    public PopularityResult {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(policyVersion, "policyVersion");
    }
}
