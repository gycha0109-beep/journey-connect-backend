package com.jc.recommendation.policy;

import java.time.Instant;
import java.util.Objects;

public record ColdStartPolicy(
        String policyVersion,
        Instant effectiveFrom,
        ExplicitPreferenceWeights explicitPreference,
        EmptyProfileWeights emptyProfile
) implements VersionedPolicy {
    public ColdStartPolicy {
        Objects.requireNonNull(policyVersion, "policyVersion");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        Objects.requireNonNull(explicitPreference, "explicitPreference");
        Objects.requireNonNull(emptyProfile, "emptyProfile");
    }

    public record ExplicitPreferenceWeights(
            double explicitPreferenceMatch,
            double freshness,
            double popularity,
            double explorationDiversity
    ) {
        public double total() {
            return explicitPreferenceMatch + freshness + popularity + explorationDiversity;
        }
    }

    public record EmptyProfileWeights(
            double freshness,
            double popularity,
            double explorationDiversity
    ) {
        public double total() {
            return freshness + popularity + explorationDiversity;
        }
    }
}
