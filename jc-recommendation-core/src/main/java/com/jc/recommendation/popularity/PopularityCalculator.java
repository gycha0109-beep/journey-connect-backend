package com.jc.recommendation.popularity;

import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.model.popularity.CalculatePopularityInput;
import com.jc.recommendation.model.popularity.PopularityEngagementSnapshot;
import com.jc.recommendation.model.popularity.PopularityNotApplicableReason;
import com.jc.recommendation.model.popularity.PopularityResult;
import com.jc.recommendation.model.popularity.PopularityStatus;
import com.jc.recommendation.model.popularity.PopularityTrustStatus;
import com.jc.recommendation.policy.PopularityPolicy;
import com.jc.recommendation.policy.ScoringPolicies;
import com.jc.recommendation.support.StrictUtcMilliseconds;

import java.util.HashSet;
import java.util.Map;

public final class PopularityCalculator {
    private static final long MAX_SAFE_INTEGER = 9_007_199_254_740_991L;
    private static final double POLICY_SUM_EPSILON = 1.0e-12;

    private PopularityCalculator() {
    }

    public static PopularityResult calculate(CalculatePopularityInput input) {
        assertNonBlank(input.entityId(), "entityId");
        PopularityPolicy policy = input.policy() == null ? ScoringPolicies.POPULARITY_V1 : input.policy();
        validatePolicy(policy);
        long referenceTimeMs = StrictUtcMilliseconds.parseEpochMilli(input.referenceTime(), "referenceTime");

        SnapshotTimes snapshotTimes = null;
        if (input.snapshot() != null) {
            snapshotTimes = validateSnapshot(
                    input.snapshot(),
                    input.entityId(),
                    input.entityType(),
                    referenceTimeMs
            );
        }

        if (!policy.eligibleEntityTypes().contains(input.entityType())) {
            return notApplicable(
                    input,
                    policy,
                    PopularityNotApplicableReason.UNSUPPORTED_ENTITY_TYPE,
                    input.snapshot()
            );
        }
        if (input.snapshot() == null) {
            return notApplicable(input, policy, PopularityNotApplicableReason.MISSING_SNAPSHOT, null);
        }
        if (input.snapshot().trustStatus() != PopularityTrustStatus.TRUSTED) {
            return notApplicable(
                    input,
                    policy,
                    PopularityNotApplicableReason.UNTRUSTED_SNAPSHOT,
                    input.snapshot()
            );
        }
        if (snapshotTimes == null) {
            throw new IllegalStateException("Validated snapshot timing is required");
        }

        Integer windowDays = policy.windowDaysByEntityType().get(input.entityType());
        if (windowDays == null || windowDays <= 0) {
            throw new IllegalArgumentException(
                    "Missing valid popularity window for " + input.entityType().wireValue()
            );
        }
        long expectedWindowMilliseconds = Math.multiplyExact(
                windowDays.longValue(),
                policy.millisecondsPerDay()
        );
        long actualWindowMilliseconds = snapshotTimes.windowEndMs() - snapshotTimes.windowStartMs();
        if (actualWindowMilliseconds != expectedWindowMilliseconds) {
            throw new IllegalArgumentException(
                    "Popularity window must equal " + windowDays + " days exactly"
            );
        }

        PopularityEngagementSnapshot snapshot = input.snapshot();
        if (snapshot.uniqueExposureCount() < policy.minimumUniqueExposure()) {
            return notApplicable(
                    input,
                    policy,
                    PopularityNotApplicableReason.INSUFFICIENT_UNIQUE_EXPOSURE,
                    snapshot
            );
        }

        long exposure = snapshot.uniqueExposureCount();
        double likeLowerBound = wilsonLowerBound(snapshot.uniqueLikeActorCount(), exposure, policy.zScore());
        double saveLowerBound = wilsonLowerBound(snapshot.uniqueSaveActorCount(), exposure, policy.zScore());
        double shareLowerBound = wilsonLowerBound(snapshot.uniqueShareActorCount(), exposure, policy.zScore());
        double qualityScore = likeLowerBound * policy.signalWeights().like()
                + saveLowerBound * policy.signalWeights().save()
                + shareLowerBound * policy.signalWeights().share();
        Integer referenceExposure = policy.referenceExposureByEntityType().get(input.entityType());
        if (referenceExposure == null || referenceExposure <= 0) {
            throw new IllegalArgumentException(
                    "Missing valid reference exposure for " + input.entityType().wireValue()
            );
        }
        double volumeEvidence = clamp(
                Math.log1p(exposure) / Math.log1p(referenceExposure),
                0.0,
                1.0
        );
        double evidenceMultiplier = policy.baseEvidenceMultiplier()
                + policy.volumeEvidenceWeight() * volumeEvidence;
        double score = clamp(
                qualityScore * evidenceMultiplier,
                policy.scoreMinimum(),
                policy.scoreMaximum()
        );
        assertFiniteUnit(likeLowerBound, "likeLowerBound");
        assertFiniteUnit(saveLowerBound, "saveLowerBound");
        assertFiniteUnit(shareLowerBound, "shareLowerBound");
        assertFiniteUnit(qualityScore, "qualityScore");
        assertFiniteUnit(volumeEvidence, "volumeEvidence");
        assertFiniteUnit(evidenceMultiplier, "evidenceMultiplier");
        assertFiniteUnit(score, "score");

        return new PopularityResult(
                input.entityId(),
                input.entityType(),
                snapshot.snapshotId(),
                PopularityStatus.SCORED,
                score,
                qualityScore,
                volumeEvidence,
                evidenceMultiplier,
                likeLowerBound,
                saveLowerBound,
                shareLowerBound,
                exposure,
                null,
                policy.policyVersion()
        );
    }

    private static void validatePolicy(PopularityPolicy policy) {
        assertNonBlank(policy.policyVersion(), "policyVersion");
        if (policy.scoreMinimum() != 0.0 || policy.scoreMaximum() != 1.0) {
            throw new IllegalArgumentException("Popularity score range must be exactly 0..1");
        }
        if (policy.millisecondsPerDay() != 86_400_000L) {
            throw new IllegalArgumentException("millisecondsPerDay must equal 86400000");
        }
        if (policy.minimumUniqueExposure() < 1) {
            throw new IllegalArgumentException("minimumUniqueExposure must be a positive safe integer");
        }
        if (!Double.isFinite(policy.zScore()) || policy.zScore() <= 0.0) {
            throw new IllegalArgumentException("zScore must be a positive finite number");
        }
        if (new HashSet<>(policy.eligibleEntityTypes()).size() != policy.eligibleEntityTypes().size()) {
            throw new IllegalArgumentException("eligibleEntityTypes must not contain duplicates");
        }

        for (RecommendationEntityType entityType : RecommendationEntityType.values()) {
            boolean eligible = policy.eligibleEntityTypes().contains(entityType);
            Integer windowDays = policy.windowDaysByEntityType().get(entityType);
            Integer referenceExposure = policy.referenceExposureByEntityType().get(entityType);
            if (eligible) {
                if (windowDays == null || windowDays <= 0) {
                    throw new IllegalArgumentException(
                            "Invalid window days for " + entityType.wireValue()
                    );
                }
                if (referenceExposure == null
                        || referenceExposure < policy.minimumUniqueExposure()) {
                    throw new IllegalArgumentException(
                            "referenceExposure for " + entityType.wireValue()
                                    + " must be a safe integer >= minimumUniqueExposure"
                    );
                }
            } else if (windowDays != null || referenceExposure != null) {
                throw new IllegalArgumentException(
                        "Unsupported entity type must not define popularity policy values: "
                                + entityType.wireValue()
                );
            }
        }
        assertFiniteUnit(policy.signalWeights().like(), "signalWeights.like");
        assertFiniteUnit(policy.signalWeights().save(), "signalWeights.save");
        assertFiniteUnit(policy.signalWeights().share(), "signalWeights.share");
        double signalWeightSum = policy.signalWeights().like()
                + policy.signalWeights().save()
                + policy.signalWeights().share();
        if (Math.abs(signalWeightSum - 1.0) > POLICY_SUM_EPSILON) {
            throw new IllegalArgumentException("signalWeights must sum to 1 within epsilon");
        }
        assertFiniteUnit(policy.baseEvidenceMultiplier(), "baseEvidenceMultiplier");
        assertFiniteUnit(policy.volumeEvidenceWeight(), "volumeEvidenceWeight");
        if (Math.abs(
                policy.baseEvidenceMultiplier() + policy.volumeEvidenceWeight() - 1.0
        ) > POLICY_SUM_EPSILON) {
            throw new IllegalArgumentException(
                    "evidence multiplier components must sum to 1 within epsilon"
            );
        }
    }

    private static SnapshotTimes validateSnapshot(
            PopularityEngagementSnapshot snapshot,
            String inputEntityId,
            RecommendationEntityType inputEntityType,
            long referenceTimeMs
    ) {
        assertNonBlank(snapshot.snapshotId(), "snapshotId");
        assertNonBlank(snapshot.entityId(), "snapshot entityId");
        assertNonBlank(snapshot.aggregationPolicyVersion(), "aggregationPolicyVersion");
        assertNonBlank(snapshot.antiAbusePolicyVersion(), "antiAbusePolicyVersion");
        long windowStartMs = StrictUtcMilliseconds.parseEpochMilli(snapshot.windowStart(), "windowStart");
        long windowEndMs = StrictUtcMilliseconds.parseEpochMilli(snapshot.windowEnd(), "windowEnd");
        if (windowStartMs >= windowEndMs) {
            throw new IllegalArgumentException("windowStart must be earlier than windowEnd");
        }

        assertNonNegativeSafeInteger(snapshot.uniqueExposureCount(), "uniqueExposureCount");
        assertNonNegativeSafeInteger(snapshot.uniqueLikeActorCount(), "uniqueLikeActorCount");
        assertNonNegativeSafeInteger(snapshot.uniqueSaveActorCount(), "uniqueSaveActorCount");
        assertNonNegativeSafeInteger(snapshot.uniqueShareActorCount(), "uniqueShareActorCount");
        assertNonNegativeSafeInteger(snapshot.rawEventCount(), "rawEventCount");
        assertNonNegativeSafeInteger(snapshot.acceptedEventCount(), "acceptedEventCount");
        assertNonNegativeSafeInteger(snapshot.rejectedEventCount(), "rejectedEventCount");
        if (snapshot.uniqueLikeActorCount() > snapshot.uniqueExposureCount()) {
            throw new IllegalArgumentException("like actors must not exceed exposure actors");
        }
        if (snapshot.uniqueSaveActorCount() > snapshot.uniqueExposureCount()) {
            throw new IllegalArgumentException("save actors must not exceed exposure actors");
        }
        if (snapshot.uniqueShareActorCount() > snapshot.uniqueExposureCount()) {
            throw new IllegalArgumentException("share actors must not exceed exposure actors");
        }
        long eventTotal;
        try {
            eventTotal = Math.addExact(snapshot.acceptedEventCount(), snapshot.rejectedEventCount());
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "acceptedEventCount + rejectedEventCount must equal rawEventCount as a safe integer",
                    exception
            );
        }
        if (eventTotal > MAX_SAFE_INTEGER || eventTotal != snapshot.rawEventCount()) {
            throw new IllegalArgumentException(
                    "acceptedEventCount + rejectedEventCount must equal rawEventCount as a safe integer"
            );
        }
        if (snapshot.acceptedEventCount() < snapshot.uniqueExposureCount()) {
            throw new IllegalArgumentException(
                    "acceptedEventCount must be at least uniqueExposureCount"
            );
        }
        if (!snapshot.entityId().equals(inputEntityId)) {
            throw new IllegalArgumentException("snapshot entityId must match input entityId");
        }
        if (snapshot.entityType() != inputEntityType) {
            throw new IllegalArgumentException("snapshot entityType must match input entityType");
        }
        if (windowEndMs != referenceTimeMs) {
            throw new IllegalArgumentException("windowEnd must equal referenceTime by instant");
        }
        return new SnapshotTimes(windowStartMs, windowEndMs);
    }

    private static PopularityResult notApplicable(
            CalculatePopularityInput input,
            PopularityPolicy policy,
            PopularityNotApplicableReason reason,
            PopularityEngagementSnapshot snapshot
    ) {
        return new PopularityResult(
                input.entityId(),
                input.entityType(),
                snapshot == null ? null : snapshot.snapshotId(),
                PopularityStatus.NOT_APPLICABLE,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                snapshot == null ? null : snapshot.uniqueExposureCount(),
                reason,
                policy.policyVersion()
        );
    }

    private static double wilsonLowerBound(long successCount, long totalCount, double zScore) {
        double p = successCount / (double) totalCount;
        double zSquared = zScore * zScore;
        double denominator = 1.0 + zSquared / totalCount;
        double center = p + zSquared / (2.0 * totalCount);
        double margin = zScore * Math.sqrt(
                (p * (1.0 - p) + zSquared / (4.0 * totalCount)) / totalCount
        );
        double result = (center - margin) / denominator;
        if (!Double.isFinite(result)) {
            throw new IllegalArgumentException("Wilson lower bound must be finite");
        }
        return clamp(result, 0.0, 1.0);
    }

    private static void assertNonBlank(String value, String fieldName) {
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    private static void assertNonNegativeSafeInteger(long value, String fieldName) {
        if (value < 0L || value > MAX_SAFE_INTEGER) {
            throw new IllegalArgumentException(
                    fieldName + " must be a non-negative safe integer"
            );
        }
    }

    private static void assertFiniteUnit(double value, String fieldName) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(
                    fieldName + " must be finite within 0..1"
            );
        }
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.min(maximum, Math.max(minimum, value));
    }

    private record SnapshotTimes(long windowStartMs, long windowEndMs) {
    }
}
