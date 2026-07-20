package com.jc.recommendation.policy;

import java.time.Instant;
import java.util.Objects;

public record CandidateLimitPolicy(
        String policyVersion,
        Instant effectiveFrom,
        int maxCandidatesToScore,
        int defaultResultLimit,
        int hardResultLimit
) implements VersionedPolicy {
    public CandidateLimitPolicy {
        Objects.requireNonNull(policyVersion, "policyVersion");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
    }
}
