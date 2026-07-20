package com.jc.recommendation.policy;

import com.jc.recommendation.model.event.EventType;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record OfflineEvaluationPolicy(
        String policyVersion,
        Instant effectiveFrom,
        String expectedExposureSchemaVersion,
        String expectedTracePolicyVersion,
        String expectedRankingPolicyVersion,
        String expectedCursorVersion,
        String expectedOutcomeValuePolicyVersion,
        String behaviorIdempotencyMode,
        String outcomeIdentityResolution,
        String multipleExposureRule,
        String replayCollection,
        String comparisonSnapshotRule,
        List<Integer> cutoffs,
        int evaluatorPageSize,
        int maximumReplayInputCandidates,
        int maximumCollectorPageCount,
        Map<EventType, Long> attributionWindowMsByEventType,
        int minimumReplayCaseCount,
        int minimumAttributedOutcomeEventCount,
        double minimumCommonSupportCoverageAt10,
        double exactReplayRequiredRate,
        int maximumInvariantViolationCount,
        String outcomeInterpretation,
        String causalUpliftWithoutPropensity,
        String automaticProductionRollout
) implements VersionedPolicy {
    public OfflineEvaluationPolicy {
        Objects.requireNonNull(policyVersion, "policyVersion");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        Objects.requireNonNull(expectedExposureSchemaVersion, "expectedExposureSchemaVersion");
        Objects.requireNonNull(expectedTracePolicyVersion, "expectedTracePolicyVersion");
        Objects.requireNonNull(expectedRankingPolicyVersion, "expectedRankingPolicyVersion");
        Objects.requireNonNull(expectedCursorVersion, "expectedCursorVersion");
        Objects.requireNonNull(expectedOutcomeValuePolicyVersion, "expectedOutcomeValuePolicyVersion");
        Objects.requireNonNull(behaviorIdempotencyMode, "behaviorIdempotencyMode");
        Objects.requireNonNull(outcomeIdentityResolution, "outcomeIdentityResolution");
        Objects.requireNonNull(multipleExposureRule, "multipleExposureRule");
        Objects.requireNonNull(replayCollection, "replayCollection");
        Objects.requireNonNull(comparisonSnapshotRule, "comparisonSnapshotRule");
        cutoffs = List.copyOf(cutoffs);
        EnumMap<EventType, Long> copy = new EnumMap<>(EventType.class);
        copy.putAll(attributionWindowMsByEventType);
        attributionWindowMsByEventType = Collections.unmodifiableMap(copy);
        Objects.requireNonNull(outcomeInterpretation, "outcomeInterpretation");
        Objects.requireNonNull(causalUpliftWithoutPropensity, "causalUpliftWithoutPropensity");
        Objects.requireNonNull(automaticProductionRollout, "automaticProductionRollout");
    }
}
