package com.jc.recommendation.p1.policy;

import com.jc.recommendation.policy.VersionedPolicy;
import java.time.Instant;
import java.util.Objects;

public record P1ScorePolicy(
        String policyVersion,
        Instant effectiveFrom,
        double contextWeight,
        double interestWeight,
        double freshnessWeight,
        double popularityWeight,
        double freshnessHalfLifeDays,
        double popularityCompressionExponent,
        double lowExposureMaximumBoost,
        int lowExposureThreshold,
        double neutralInterestPrior) implements VersionedPolicy {

    private static final double EPSILON = 1.0e-12d;

    public P1ScorePolicy {
        Objects.requireNonNull(policyVersion, "policyVersion");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        if (policyVersion.isBlank()) {
            throw new IllegalArgumentException("policyVersion must not be blank");
        }
        validateUnit(contextWeight, "contextWeight");
        validateUnit(interestWeight, "interestWeight");
        validateUnit(freshnessWeight, "freshnessWeight");
        validateUnit(popularityWeight, "popularityWeight");
        validatePositive(freshnessHalfLifeDays, "freshnessHalfLifeDays");
        if (!Double.isFinite(popularityCompressionExponent)
                || popularityCompressionExponent <= 0.0d
                || popularityCompressionExponent > 1.0d) {
            throw new IllegalArgumentException("popularityCompressionExponent must be within (0,1]");
        }
        validateUnit(lowExposureMaximumBoost, "lowExposureMaximumBoost");
        if (lowExposureThreshold < 1) {
            throw new IllegalArgumentException("lowExposureThreshold must be positive");
        }
        validateUnit(neutralInterestPrior, "neutralInterestPrior");
        double totalWeight = contextWeight + interestWeight + freshnessWeight + popularityWeight;
        if (StrictMath.abs(totalWeight - 1.0d) > EPSILON) {
            throw new IllegalArgumentException("score weights must sum to 1.0");
        }
    }

    public double weightTotal() {
        return contextWeight + interestWeight + freshnessWeight + popularityWeight;
    }

    private static void validateUnit(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(name + " must be finite and within [0,1]");
        }
    }

    private static void validatePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0d) {
            throw new IllegalArgumentException(name + " must be positive and finite");
        }
    }
}
