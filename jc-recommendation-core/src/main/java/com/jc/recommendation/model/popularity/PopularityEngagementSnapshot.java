package com.jc.recommendation.model.popularity;

import com.jc.recommendation.model.entity.RecommendationEntityType;

import java.util.Objects;

public record PopularityEngagementSnapshot(
        String snapshotId,
        String entityId,
        RecommendationEntityType entityType,
        String windowStart,
        String windowEnd,
        long uniqueExposureCount,
        long uniqueLikeActorCount,
        long uniqueSaveActorCount,
        long uniqueShareActorCount,
        long rawEventCount,
        long acceptedEventCount,
        long rejectedEventCount,
        PopularityTrustStatus trustStatus,
        String aggregationPolicyVersion,
        String antiAbusePolicyVersion
) {
    public PopularityEngagementSnapshot {
        Objects.requireNonNull(snapshotId, "snapshotId");
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(windowStart, "windowStart");
        Objects.requireNonNull(windowEnd, "windowEnd");
        Objects.requireNonNull(trustStatus, "trustStatus");
        Objects.requireNonNull(aggregationPolicyVersion, "aggregationPolicyVersion");
        Objects.requireNonNull(antiAbusePolicyVersion, "antiAbusePolicyVersion");
    }
}
