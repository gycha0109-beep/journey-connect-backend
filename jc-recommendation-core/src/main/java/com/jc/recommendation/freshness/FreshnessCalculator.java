package com.jc.recommendation.freshness;

import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.model.freshness.CalculateFreshnessInput;
import com.jc.recommendation.model.freshness.FreshnessNotApplicableReason;
import com.jc.recommendation.model.freshness.FreshnessResult;
import com.jc.recommendation.model.freshness.FreshnessStatus;
import com.jc.recommendation.model.freshness.FreshnessTimestampSource;
import com.jc.recommendation.policy.FreshnessPolicy;
import com.jc.recommendation.policy.ScoringPolicies;
import com.jc.recommendation.support.StrictUtcMilliseconds;

import java.util.HashSet;
import java.util.Map;

public final class FreshnessCalculator {
    private FreshnessCalculator() {
    }

    public static FreshnessResult calculate(CalculateFreshnessInput input) {
        assertNonBlank(input.entityId(), "entityId");
        FreshnessPolicy policy = input.policy() == null ? ScoringPolicies.FRESHNESS_V1 : input.policy();
        validatePolicy(policy);
        long referenceTimeMs = StrictUtcMilliseconds.parseEpochMilli(input.referenceTime(), "referenceTime");
        Long freshnessTimeMs = validateTimestampPair(
                input.freshnessTimestamp(),
                input.timestampSource(),
                policy
        );

        if (freshnessTimeMs != null && freshnessTimeMs > referenceTimeMs) {
            throw new IllegalArgumentException("freshnessTimestamp must not be in the future");
        }

        if (!policy.eligibleEntityTypes().contains(input.entityType())) {
            return new FreshnessResult(
                    input.entityId(),
                    input.entityType(),
                    FreshnessStatus.NOT_APPLICABLE,
                    null,
                    null,
                    null,
                    null,
                    input.freshnessTimestamp(),
                    input.timestampSource(),
                    FreshnessNotApplicableReason.UNSUPPORTED_ENTITY_TYPE,
                    policy.policyVersion()
            );
        }

        if (freshnessTimeMs == null) {
            return new FreshnessResult(
                    input.entityId(),
                    input.entityType(),
                    FreshnessStatus.NOT_APPLICABLE,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    FreshnessNotApplicableReason.MISSING_FRESHNESS_TIMESTAMP,
                    policy.policyVersion()
            );
        }

        Double halfLifeDays = policy.halfLifeDaysByEntityType().get(input.entityType());
        if (halfLifeDays == null || !Double.isFinite(halfLifeDays) || halfLifeDays <= 0.0) {
            throw new IllegalArgumentException("Missing valid half-life for " + input.entityType().wireValue());
        }
        long ageMilliseconds = referenceTimeMs - freshnessTimeMs;
        double ageDays = ageMilliseconds / (double) policy.millisecondsPerDay();
        double rawScore = StrictMath.pow(2.0, -ageDays / halfLifeDays);
        if (!Double.isFinite(rawScore)) {
            throw new IllegalArgumentException("Freshness score must be finite");
        }
        double score = clamp(rawScore, policy.scoreMinimum(), policy.scoreMaximum());
        return new FreshnessResult(
                input.entityId(),
                input.entityType(),
                FreshnessStatus.SCORED,
                score,
                ageMilliseconds,
                ageDays,
                halfLifeDays,
                input.freshnessTimestamp(),
                input.timestampSource(),
                null,
                policy.policyVersion()
        );
    }

    private static Long validateTimestampPair(
            String timestamp,
            FreshnessTimestampSource source,
            FreshnessPolicy policy
    ) {
        if ((timestamp == null) != (source == null)) {
            throw new IllegalArgumentException(
                    "freshnessTimestamp and timestampSource must both be present or both be null"
            );
        }
        if (timestamp == null) {
            return null;
        }
        if (!policy.allowedTimestampSources().contains(source)) {
            throw new IllegalArgumentException(
                    "Timestamp source is not allowed: " + source.wireValue()
            );
        }
        return StrictUtcMilliseconds.parseEpochMilli(timestamp, "freshnessTimestamp");
    }

    private static void validatePolicy(FreshnessPolicy policy) {
        assertNonBlank(policy.policyVersion(), "policyVersion");
        if (!Double.isFinite(policy.scoreMinimum())
                || !Double.isFinite(policy.scoreMaximum())
                || policy.scoreMinimum() != 0.0
                || policy.scoreMaximum() != 1.0) {
            throw new IllegalArgumentException("Freshness score range must be exactly 0..1");
        }
        if (policy.millisecondsPerDay() != 86_400_000L) {
            throw new IllegalArgumentException("millisecondsPerDay must equal 86400000");
        }
        if (new HashSet<>(policy.eligibleEntityTypes()).size() != policy.eligibleEntityTypes().size()) {
            throw new IllegalArgumentException("eligibleEntityTypes must not contain duplicates");
        }
        if (new HashSet<>(policy.allowedTimestampSources()).size() != policy.allowedTimestampSources().size()) {
            throw new IllegalArgumentException("allowedTimestampSources must not contain duplicates");
        }
        for (RecommendationEntityType entityType : policy.eligibleEntityTypes()) {
            Double halfLife = policy.halfLifeDaysByEntityType().get(entityType);
            if (halfLife == null || !Double.isFinite(halfLife) || halfLife <= 0.0) {
                throw new IllegalArgumentException("Invalid half-life for " + entityType.wireValue());
            }
        }
        for (Map.Entry<RecommendationEntityType, Double> entry : policy.halfLifeDaysByEntityType().entrySet()) {
            if (!policy.eligibleEntityTypes().contains(entry.getKey())) {
                throw new IllegalArgumentException(
                        "Unsupported entity type must not define half-life: " + entry.getKey().wireValue()
                );
            }
        }
    }

    private static void assertNonBlank(String value, String fieldName) {
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.min(maximum, Math.max(minimum, value));
    }
}
