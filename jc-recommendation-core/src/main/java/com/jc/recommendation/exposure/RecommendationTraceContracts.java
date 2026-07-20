package com.jc.recommendation.exposure;

import com.jc.recommendation.model.diversity.DiversityDimensionCounts;
import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.model.exposure.RecommendationExposureCandidate;
import com.jc.recommendation.model.exposure.RecommendationExposureEventV1;
import com.jc.recommendation.model.exposure.RecommendationExposureOrigin;
import com.jc.recommendation.model.exploration.ExplorationSlotDecisionStatus;
import com.jc.recommendation.model.ranking.RankingResultStatus;
import com.jc.recommendation.policy.CandidateLimitPolicies;
import com.jc.recommendation.policy.RecommendationTracePolicies;
import com.jc.recommendation.policy.RecommendationTracePolicy;
import com.jc.recommendation.support.StrictUtcMilliseconds;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public final class RecommendationTraceContracts {
    private static final Pattern LOWER_HEX_64 = Pattern.compile("^[0-9a-f]{64}$");
    private static final long NEGATIVE_ZERO_BITS = Double.doubleToRawLongBits(-0.0d);

    private RecommendationTraceContracts() {
    }

    public static void validatePolicy(RecommendationTracePolicy policy) {
        RecommendationTracePolicy expected = RecommendationTracePolicies.V1;
        if (!expected.equals(policy)) {
            throw new IllegalArgumentException("INVALID_TRACE_POLICY: policy must exactly match recommendation-trace-v1");
        }
        if (policy.maximumPageCandidateCount() != CandidateLimitPolicies.HARD_RESULT_LIMIT) {
            throw new IllegalArgumentException("INVALID_TRACE_POLICY: maximumPageCandidateCount");
        }
    }

    public static void validateEvent(RecommendationExposureEventV1 event) {
        if (event == null) {
            invalid("event must be non-null");
        }
        if (!"recommendation-exposure-v1".equals(event.schemaVersion())) {
            invalid("schemaVersion");
        }
        nonblank(event.eventId(), "eventId");
        nonblank(event.idempotencyKey(), "idempotencyKey");
        nonblank(event.recommendationRunId(), "recommendationRunId");
        nonblank(event.userId(), "userId");
        nonblank(event.sessionId(), "sessionId");
        nonblank(event.contextId(), "contextId");
        nonblank(event.rankingSnapshotId(), "rankingSnapshotId");
        nonblank(event.metadataSnapshotId(), "metadataSnapshotId");
        nonblank(event.explorationSnapshotId(), "explorationSnapshotId");
        nonblank(event.rankingPolicyVersion(), "rankingPolicyVersion");
        nonblank(event.baseIntegrationPolicyVersion(), "baseIntegrationPolicyVersion");
        nonblank(event.baseRankingPolicyVersion(), "baseRankingPolicyVersion");
        nonblank(event.scorePolicyVersion(), "scorePolicyVersion");
        nonblank(event.diversityPolicyVersion(), "diversityPolicyVersion");
        nonblank(event.explorationPolicyVersion(), "explorationPolicyVersion");
        nonblank(event.explorationSeed(), "explorationSeed");
        StrictUtcMilliseconds.parseEpochMilli(event.servedAt(), "servedAt");
        if (!"ranking-cursor-v3".equals(event.cursorVersion())
                || !"ranking-v3".equals(event.rankingPolicyVersion())) {
            invalid("version vector");
        }
        if (event.explorationSeed().getBytes(StandardCharsets.UTF_8).length > 128) {
            invalid("explorationSeed exceeds 128 UTF-8 bytes");
        }
        if (!LOWER_HEX_64.matcher(event.replayKey()).matches()
                || !LOWER_HEX_64.matcher(event.pageFingerprint()).matches()) {
            invalid("fingerprint format");
        }
        nonblank(event.componentPolicyVersions().contextMatch(), "componentPolicyVersions.context_match");
        nonblank(event.componentPolicyVersions().interestMatch(), "componentPolicyVersions.interest_match");
        nonblank(event.componentPolicyVersions().freshness(), "componentPolicyVersions.freshness");
        nonblank(event.componentPolicyVersions().popularity(), "componentPolicyVersions.popularity");
        if (event.requestedLimit() != null) {
            positive(event.requestedLimit(), "requestedLimit");
        }
        positive(event.effectiveLimit(), "effectiveLimit");
        if (event.effectiveLimit() > RecommendationTracePolicies.V1.maximumPageCandidateCount()) {
            invalid("effectiveLimit");
        }
        nonnegative(event.pageCandidateCount(), "pageCandidateCount");
        nonnegative(event.inputCount(), "inputCount");
        nonnegative(event.finalRankedCandidateCount(), "finalRankedCandidateCount");
        nonnegative(event.terminalCandidateCount(), "terminalCandidateCount");
        if (event.inputCount() != event.finalRankedCandidateCount() + event.terminalCandidateCount()) {
            invalid("event count invariant");
        }
        validateDiversity(event.diversitySummary().relaxationCountByDimension());
        validateDiversity(event.diversitySummary().violationCountByDimension());
        validateDiversity(event.diversitySummary().missingMetadataCountByDimension());
        nonnegative(event.diversitySummary().movedCandidateCount(), "diversity moved count");
        nonnegative(event.diversitySummary().maxPromotionObserved(), "diversity promotion");
        nonnegative(event.diversitySummary().maxDemotionObserved(), "diversity demotion");
        nonnegative(event.diversitySummary().movementBoundForcedCount(), "diversity movement bound");
        var exploration = event.explorationSummary();
        nonnegative(exploration.structurallyEligibleCandidateCount(), "structurallyEligibleCandidateCount");
        nonnegative(exploration.eligibleCandidateCount(), "eligibleCandidateCount");
        nonnegative(exploration.insertedCandidateCount(), "insertedCandidateCount");
        nonnegative(exploration.skippedSlotCount(), "skippedSlotCount");
        nonnegative(exploration.statusReasonRejectedCount(), "statusReasonRejectedCount");
        nonnegative(exploration.entityTypeRejectedCount(), "entityTypeRejectedCount");
        nonnegative(exploration.exposureRejectedCount(), "exposureRejectedCount");
        nonnegative(exploration.qualityEvidenceRejectedCount(), "qualityEvidenceRejectedCount");
        nonnegative(exploration.qualityFloorRejectedCount(), "qualityFloorRejectedCount");
        nonnegative(exploration.diversityGuardRejectedEvaluationCount(), "diversityGuardRejectedEvaluationCount");
        for (Integer rank : exploration.insertedTargetRanks()) {
            positive(rank, "insertedTargetRank");
        }
        nonblank(exploration.policyVersion(), "explorationSummary.policyVersion");
        for (var decision : exploration.slotDecisions()) {
            positive(decision.targetInsertionRank(), "slot targetInsertionRank");
            ExplorationSlotDecisionStatus status = decision.status();
            if (status == null) {
                invalid("slot status");
            }
        }
        if (event.candidates().size() != event.pageCandidateCount()
                || event.pageCandidateCount() > event.effectiveLimit()) {
            invalid("page candidate count");
        }
        Set<String> identities = new HashSet<>();
        for (int index = 0; index < event.candidates().size(); index++) {
            validateCandidate(event.candidates().get(index), index, identities);
        }
        if (event.candidates().isEmpty()) {
            if (event.pageStartRank() != null || event.pageEndRank() != null || event.hasNextPage()) {
                invalid("empty page contract");
            }
            if (event.rankingStatus() == RankingResultStatus.EMPTY
                    && (event.rankingEmptyReason() == null || event.finalRankedCandidateCount() != 0)) {
                invalid("empty terminal result");
            }
            if (event.rankingStatus() == RankingResultStatus.RANKED
                    && (event.rankingEmptyReason() != null || event.finalRankedCandidateCount() < 1)) {
                invalid("empty ranked page");
            }
        } else {
            positive(event.pageStartRank(), "pageStartRank");
            positive(event.pageEndRank(), "pageEndRank");
            if (event.pageStartRank() != event.candidates().getFirst().absoluteRank()
                    || event.pageEndRank() != event.candidates().getLast().absoluteRank()) {
                invalid("page boundary");
            }
            for (int index = 0; index < event.candidates().size(); index++) {
                RecommendationExposureCandidate candidate = event.candidates().get(index);
                if (candidate.absoluteRank() != event.pageStartRank() + index) {
                    invalid("candidate rank continuity");
                }
                if (candidate.absoluteRank() > event.finalRankedCandidateCount()) {
                    invalid("candidate rank beyond final count");
                }
            }
        }
        String replay = RecommendationTraceCanonical.hashCanonical(
                RecommendationTraceCanonical.replayProjection(event));
        String page = RecommendationTraceCanonical.hashCanonical(
                RecommendationTraceCanonical.pageProjection(event));
        if (!event.replayKey().equals(replay) || !event.pageFingerprint().equals(page)) {
            invalid("fingerprint mismatch");
        }
    }

    private static void validateCandidate(
            RecommendationExposureCandidate candidate,
            int index,
            Set<String> identities
    ) {
        nonblank(candidate.entityId(), "candidate[" + index + "].entityId");
        if (candidate.entityType() == RecommendationEntityType.USER) {
            invalid("candidate[" + index + "].entityType");
        }
        positive(candidate.absoluteRank(), "candidate[" + index + "].absoluteRank");
        positive(candidate.pagePosition(), "candidate[" + index + "].pagePosition");
        if (candidate.pagePosition() != index + 1) {
            invalid("candidate[" + index + "].pagePosition continuity");
        }
        String identity = candidate.entityType().wireValue() + '\0' + candidate.entityId();
        if (!identities.add(identity)) {
            invalid("duplicate page candidate identity");
        }
        if (candidate.origin() == RecommendationExposureOrigin.PERSONALIZED) {
            finite01(candidate.score(), "candidate[" + index + "].score");
            boolean negativeZero = Double.doubleToRawLongBits(candidate.score()) == NEGATIVE_ZERO_BITS;
            if (candidate.scoreIsNegativeZero() != negativeZero) {
                invalid("candidate[" + index + "].signed zero");
            }
            positive(candidate.baseAbsoluteRank(), "candidate[" + index + "].baseAbsoluteRank");
            positive(candidate.diversifiedAbsoluteRank(), "candidate[" + index + "].diversifiedAbsoluteRank");
            if (candidate.explorationQualityScore() != null || candidate.recentExposureCount() != null
                    || candidate.seededTieBreakKey() != null || candidate.explorationPoolRank() != null
                    || candidate.targetInsertionRank() != null) {
                invalid("candidate[" + index + "] personalized null contract");
            }
        } else {
            if (candidate.entityType() != RecommendationEntityType.POST
                    && candidate.entityType() != RecommendationEntityType.JOURNEY) {
                invalid("candidate[" + index + "] exploration entityType");
            }
            if (candidate.score() != null || candidate.scoreIsNegativeZero()
                    || candidate.baseAbsoluteRank() != null || candidate.diversifiedAbsoluteRank() != null) {
                invalid("candidate[" + index + "] exploration null contract");
            }
            finite01(candidate.explorationQualityScore(), "candidate[" + index + "].explorationQualityScore");
            nonnegative(candidate.recentExposureCount(), "candidate[" + index + "].recentExposureCount");
            if (candidate.seededTieBreakKey() == null || candidate.seededTieBreakKey() < 0L
                    || candidate.seededTieBreakKey() > 0xffff_ffffL) {
                invalid("candidate[" + index + "].seededTieBreakKey");
            }
            positive(candidate.explorationPoolRank(), "candidate[" + index + "].explorationPoolRank");
            positive(candidate.targetInsertionRank(), "candidate[" + index + "].targetInsertionRank");
        }
    }

    private static void validateDiversity(DiversityDimensionCounts counts) {
        nonnegative(counts.duplicateGroup(), "dimension duplicate_group");
        nonnegative(counts.author(), "dimension author");
        nonnegative(counts.region(), "dimension region");
        nonnegative(counts.theme(), "dimension theme");
    }

    static void nonblank(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            invalid(label + " must be nonblank");
        }
    }

    static void nonnegative(Integer value, String label) {
        if (value == null || value < 0) {
            invalid(label + " must be nonnegative safe integer");
        }
    }

    static void positive(Integer value, String label) {
        if (value == null || value < 1) {
            invalid(label + " must be positive safe integer");
        }
    }

    static void finite01(Double value, String label) {
        if (value == null || !Double.isFinite(value) || value < 0.0d || value > 1.0d) {
            invalid(label + " must be finite 0..1");
        }
    }

    static void invalid(String message) {
        throw new IllegalArgumentException("INVALID_EXPOSURE_EVENT: " + message);
    }
}
