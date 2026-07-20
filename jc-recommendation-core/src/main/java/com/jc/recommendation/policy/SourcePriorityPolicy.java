package com.jc.recommendation.policy;

import com.jc.recommendation.model.feature.FeatureSource;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record SourcePriorityPolicy(
        String policyVersion,
        Instant effectiveFrom,
        List<FeatureSource> priority
) implements VersionedPolicy {
    public SourcePriorityPolicy {
        Objects.requireNonNull(policyVersion, "policyVersion");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        priority = List.copyOf(priority);
    }
}
