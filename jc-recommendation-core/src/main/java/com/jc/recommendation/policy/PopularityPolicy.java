package com.jc.recommendation.policy;

import com.jc.recommendation.model.entity.RecommendationEntityType;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record PopularityPolicy(
        String policyVersion,
        Instant effectiveFrom,
        List<RecommendationEntityType> eligibleEntityTypes,
        Map<RecommendationEntityType, Integer> windowDaysByEntityType,
        int minimumUniqueExposure,
        double zScore,
        PopularitySignalWeights signalWeights,
        Map<RecommendationEntityType, Integer> referenceExposureByEntityType,
        double baseEvidenceMultiplier,
        double volumeEvidenceWeight,
        double scoreMinimum,
        double scoreMaximum,
        long millisecondsPerDay
) implements VersionedPolicy {
    public PopularityPolicy {
        Objects.requireNonNull(policyVersion, "policyVersion");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        eligibleEntityTypes = List.copyOf(eligibleEntityTypes);
        windowDaysByEntityType = Collections.unmodifiableMap(new LinkedHashMap<>(windowDaysByEntityType));
        Objects.requireNonNull(signalWeights, "signalWeights");
        referenceExposureByEntityType = Collections.unmodifiableMap(new LinkedHashMap<>(referenceExposureByEntityType));
    }
}
