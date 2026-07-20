package com.jc.recommendation.policy;

import com.jc.recommendation.model.feature.FeatureSource;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record InterestMatchPolicy(
        String policyVersion,
        Instant effectiveFrom,
        double hardAvoidContributionThreshold,
        List<FeatureSource> hardAvoidSources,
        boolean exactFeatureMatchOnly,
        double scoreMinimum,
        double scoreMaximum
) implements VersionedPolicy {
    public InterestMatchPolicy {
        Objects.requireNonNull(policyVersion, "policyVersion");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        hardAvoidSources = List.copyOf(hardAvoidSources);
    }
}
