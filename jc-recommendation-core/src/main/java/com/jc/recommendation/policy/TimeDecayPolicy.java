package com.jc.recommendation.policy;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record TimeDecayPolicy(
        String policyVersion,
        Instant effectiveFrom,
        List<TimeDecayBucket> buckets
) implements VersionedPolicy {
    public TimeDecayPolicy {
        Objects.requireNonNull(policyVersion, "policyVersion");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        buckets = List.copyOf(buckets);
    }
}
