package com.jc.recommendation.policy;

import java.time.Instant;
import java.util.Objects;

public record SaturationPolicy(
        String policyVersion,
        Instant effectiveFrom,
        double scale
) implements VersionedPolicy {
    public SaturationPolicy {
        Objects.requireNonNull(policyVersion, "policyVersion");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
    }
}
