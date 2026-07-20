package com.jc.recommendation.policy;

import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.model.freshness.FreshnessTimestampSource;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record FreshnessPolicy(
        String policyVersion,
        Instant effectiveFrom,
        List<RecommendationEntityType> eligibleEntityTypes,
        Map<RecommendationEntityType, Double> halfLifeDaysByEntityType,
        double scoreMinimum,
        double scoreMaximum,
        long millisecondsPerDay,
        List<FreshnessTimestampSource> allowedTimestampSources
) implements VersionedPolicy {
    public FreshnessPolicy {
        Objects.requireNonNull(policyVersion, "policyVersion");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        eligibleEntityTypes = List.copyOf(eligibleEntityTypes);
        halfLifeDaysByEntityType = Collections.unmodifiableMap(new LinkedHashMap<>(halfLifeDaysByEntityType));
        allowedTimestampSources = List.copyOf(allowedTimestampSources);
    }
}
