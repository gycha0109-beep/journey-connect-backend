package com.jc.recommendation.exposure;

import com.jc.recommendation.model.exposure.BuildRecommendationExposureEventInput;
import com.jc.recommendation.model.exposure.RecommendationExposureCandidate;
import com.jc.recommendation.model.exposure.RecommendationExposureDiversitySummary;
import com.jc.recommendation.model.exposure.RecommendationExposureEventV1;
import com.jc.recommendation.model.exposure.RecommendationExposureExplorationSlotDecision;
import com.jc.recommendation.model.exposure.RecommendationExposureExplorationSummary;
import com.jc.recommendation.model.exposure.RecommendationExposureOrigin;
import com.jc.recommendation.model.exploration.InsertedExplorationCandidate;
import com.jc.recommendation.model.exploration.PersonalizedExplorationCandidate;
import com.jc.recommendation.model.integration.RankCandidatesWithExplorationResult;
import com.jc.recommendation.model.ranking.RankingResultStatus;
import com.jc.recommendation.policy.RecommendationTracePolicies;
import com.jc.recommendation.support.StrictUtcMilliseconds;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RecommendationExposureEventBuilder {
    private static final long NEGATIVE_ZERO_BITS = Double.doubleToRawLongBits(-0.0d);

    public RecommendationExposureEventV1 build(BuildRecommendationExposureEventInput input) {
        if (input == null) {
            RecommendationTraceContracts.invalid("input must be a plain object");
        }
        RecommendationTraceContracts.nonblank(input.eventId(), "eventId");
        RecommendationTraceContracts.nonblank(input.idempotencyKey(), "idempotencyKey");
        RecommendationTraceContracts.nonblank(input.recommendationRunId(), "recommendationRunId");
        RecommendationTraceContracts.nonblank(input.sessionId(), "sessionId");
        StrictUtcMilliseconds.parseEpochMilli(input.servedAt(), "servedAt");
        RecommendationTraceContracts.validatePolicy(RecommendationTracePolicies.V1);
        validateResult(input.rankingResult());

        RankCandidatesWithExplorationResult result = input.rankingResult();
        List<RecommendationExposureCandidate> candidates = projectCandidates(result);
        RecommendationExposureDiversitySummary diversitySummary = new RecommendationExposureDiversitySummary(
                result.diversitySummary().status(), result.diversitySummary().movedCandidateCount(),
                result.diversitySummary().maxPromotionObserved(), result.diversitySummary().maxDemotionObserved(),
                result.diversitySummary().movementBoundForcedCount(),
                result.diversitySummary().relaxationCountByDimension(),
                result.diversitySummary().violationCountByDimension(),
                result.diversitySummary().missingMetadataCountByDimension()
        );
        List<RecommendationExposureExplorationSlotDecision> slotDecisions = result.explorationSummary()
                .slotDecisions().stream()
                .map(value -> new RecommendationExposureExplorationSlotDecision(
                        value.targetInsertionRank(), value.status()))
                .toList();
        RecommendationExposureExplorationSummary explorationSummary = new RecommendationExposureExplorationSummary(
                result.explorationSummary().status(),
                result.explorationSummary().structurallyEligibleCandidateCount(),
                result.explorationSummary().eligibleCandidateCount(),
                result.explorationSummary().insertedCandidateCount(),
                result.explorationSummary().skippedSlotCount(),
                result.explorationSummary().statusReasonRejectedCount(),
                result.explorationSummary().entityTypeRejectedCount(),
                result.explorationSummary().exposureRejectedCount(),
                result.explorationSummary().qualityEvidenceRejectedCount(),
                result.explorationSummary().qualityFloorRejectedCount(),
                result.explorationSummary().diversityGuardRejectedEvaluationCount(),
                result.explorationSummary().insertedTargetRanks(),
                result.explorationSummary().policyVersion(),
                result.explorationSummary().seedAlgorithm(), slotDecisions
        );

        RecommendationExposureEventV1 base = event(input, result, diversitySummary, explorationSummary,
                candidates, "", "");
        String replayKey = RecommendationTraceCanonical.hashCanonical(
                RecommendationTraceCanonical.replayProjection(base));
        RecommendationExposureEventV1 withReplay = event(input, result, diversitySummary, explorationSummary,
                candidates, replayKey, "");
        String pageFingerprint = RecommendationTraceCanonical.hashCanonical(
                RecommendationTraceCanonical.pageProjection(withReplay));
        RecommendationExposureEventV1 event = event(input, result, diversitySummary, explorationSummary,
                candidates, replayKey, pageFingerprint);
        RecommendationTraceContracts.validateEvent(event);
        return event;
    }

    private static RecommendationExposureEventV1 event(
            BuildRecommendationExposureEventInput input,
            RankCandidatesWithExplorationResult result,
            RecommendationExposureDiversitySummary diversitySummary,
            RecommendationExposureExplorationSummary explorationSummary,
            List<RecommendationExposureCandidate> candidates,
            String replayKey,
            String pageFingerprint
    ) {
        return new RecommendationExposureEventV1(
                "recommendation-exposure-v1", input.eventId(), input.idempotencyKey(),
                input.recommendationRunId(), result.userId(), input.sessionId(), result.contextId(),
                input.surface(), input.servedAt(), replayKey, pageFingerprint, "ranking-cursor-v3",
                result.rankingSnapshotId(), result.metadataSnapshotId(), result.explorationSnapshotId(),
                result.policyVersion(), result.baseIntegrationPolicyVersion(), result.baseRankingPolicyVersion(),
                result.scorePolicyVersion(), result.componentPolicyVersions(), result.diversityPolicyVersion(),
                result.explorationPolicyVersion(), result.explorationSeed(), result.status(), result.emptyReason(),
                result.requestedLimit(), result.effectiveLimit(), result.pageStartRank(), result.pageEndRank(),
                candidates.size(), result.hasNextPage(), result.inputCount(), result.finalRankedCandidateCount(),
                result.terminalCandidateCount(), diversitySummary, explorationSummary, candidates
        );
    }

    private static List<RecommendationExposureCandidate> projectCandidates(
            RankCandidatesWithExplorationResult result
    ) {
        List<RecommendationExposureCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < result.rankedCandidates().size(); index++) {
            var candidate = result.rankedCandidates().get(index);
            if (candidate instanceof PersonalizedExplorationCandidate personalized) {
                RecommendationTraceContracts.finite01(personalized.score(), "candidate.score");
                RecommendationTraceContracts.positive(personalized.baseAbsoluteRank(), "candidate.baseAbsoluteRank");
                RecommendationTraceContracts.positive(
                        personalized.diversifiedAbsoluteRank(), "candidate.diversifiedAbsoluteRank");
                candidates.add(new RecommendationExposureCandidate(
                        personalized.entityId(), personalized.entityType(), personalized.absoluteRank(), index + 1,
                        RecommendationExposureOrigin.PERSONALIZED, personalized.score(),
                        Double.doubleToRawLongBits(personalized.score()) == NEGATIVE_ZERO_BITS,
                        personalized.baseAbsoluteRank(), personalized.diversifiedAbsoluteRank(),
                        null, null, null, null, null
                ));
            } else if (candidate instanceof InsertedExplorationCandidate exploration) {
                RecommendationTraceContracts.finite01(
                        exploration.explorationQualityScore(), "explorationQualityScore");
                RecommendationTraceContracts.nonnegative(
                        exploration.recentExposureCount(), "recentExposureCount");
                if (exploration.seededTieBreakKey() < 0L || exploration.seededTieBreakKey() > 0xffff_ffffL) {
                    RecommendationTraceContracts.invalid("seededTieBreakKey");
                }
                RecommendationTraceContracts.positive(exploration.explorationPoolRank(), "explorationPoolRank");
                RecommendationTraceContracts.positive(exploration.targetInsertionRank(), "targetInsertionRank");
                candidates.add(new RecommendationExposureCandidate(
                        exploration.entityId(), exploration.entityType(), exploration.absoluteRank(), index + 1,
                        RecommendationExposureOrigin.EXPLORATION, null, false, null, null,
                        exploration.explorationQualityScore(), exploration.recentExposureCount(),
                        exploration.seededTieBreakKey(), exploration.explorationPoolRank(),
                        exploration.targetInsertionRank()
                ));
            } else {
                RecommendationTraceContracts.invalid("unsupported final candidate type");
            }
        }
        return List.copyOf(candidates);
    }

    private static void validateResult(RankCandidatesWithExplorationResult result) {
        if (!RecommendationTracePolicies.V1.expectedRankingPolicyVersion().equals(result.policyVersion())) {
            RecommendationTraceContracts.invalid("only default ranking-v3 is supported");
        }
        RecommendationTraceContracts.nonblank(result.rankingSnapshotId(), "rankingSnapshotId");
        RecommendationTraceContracts.nonblank(result.metadataSnapshotId(), "metadataSnapshotId");
        RecommendationTraceContracts.nonblank(result.explorationSnapshotId(), "explorationSnapshotId");
        RecommendationTraceContracts.nonblank(result.userId(), "userId");
        RecommendationTraceContracts.nonblank(result.contextId(), "contextId");
        RecommendationTraceContracts.nonblank(result.baseIntegrationPolicyVersion(), "baseIntegrationPolicyVersion");
        RecommendationTraceContracts.nonblank(result.baseRankingPolicyVersion(), "baseRankingPolicyVersion");
        RecommendationTraceContracts.nonblank(result.scorePolicyVersion(), "scorePolicyVersion");
        RecommendationTraceContracts.nonblank(result.diversityPolicyVersion(), "diversityPolicyVersion");
        RecommendationTraceContracts.nonblank(result.explorationPolicyVersion(), "explorationPolicyVersion");
        RecommendationTraceContracts.nonblank(result.explorationSeed(), "explorationSeed");
        if (result.explorationSeed().getBytes(StandardCharsets.UTF_8).length > 128) {
            RecommendationTraceContracts.invalid("explorationSeed exceeds 128 UTF-8 bytes");
        }
        int[] counts = {
                result.inputCount(), result.scoredCandidateCount(), result.sourceTerminalCandidateCount(),
                result.personalizedCandidateCount(), result.structurallyEligibleExplorationCandidateCount(),
                result.explorationEligibleCandidateCount(), result.explorationInsertedCandidateCount(),
                result.finalRankedCandidateCount(), result.terminalCandidateCount()
        };
        for (int count : counts) {
            RecommendationTraceContracts.nonnegative(count, "count");
        }
        if (result.terminalCandidates().size() != result.terminalCandidateCount()) {
            RecommendationTraceContracts.invalid("terminal candidate count");
        }
        if (result.inputCount() != result.scoredCandidateCount() + result.sourceTerminalCandidateCount()
                || result.personalizedCandidateCount() != result.scoredCandidateCount()
                || result.finalRankedCandidateCount()
                != result.personalizedCandidateCount() + result.explorationInsertedCandidateCount()
                || result.terminalCandidateCount()
                != result.sourceTerminalCandidateCount() - result.explorationInsertedCandidateCount()
                || result.inputCount() != result.finalRankedCandidateCount() + result.terminalCandidateCount()) {
            RecommendationTraceContracts.invalid("count invariant");
        }
        RecommendationTraceContracts.positive(result.effectiveLimit(), "effectiveLimit");
        if (result.effectiveLimit() > RecommendationTracePolicies.V1.maximumPageCandidateCount()
                || result.rankedCandidates().size() > result.effectiveLimit()) {
            RecommendationTraceContracts.invalid("page limit");
        }
        if (result.requestedLimit() != null) {
            RecommendationTraceContracts.positive(result.requestedLimit(), "requestedLimit");
        }
        if (result.rankedCandidates().isEmpty()) {
            if (result.pageStartRank() != null || result.pageEndRank() != null
                    || result.hasNextPage() || result.nextCursor() != null) {
                RecommendationTraceContracts.invalid("empty page contract");
            }
            if (result.status() == RankingResultStatus.EMPTY
                    && (result.emptyReason() == null || result.finalRankedCandidateCount() != 0)) {
                RecommendationTraceContracts.invalid("empty result contract");
            }
            if (result.status() == RankingResultStatus.RANKED
                    && (result.emptyReason() != null || result.finalRankedCandidateCount() < 1)) {
                RecommendationTraceContracts.invalid("empty ranked page contract");
            }
        } else {
            int first = result.rankedCandidates().getFirst().absoluteRank();
            int last = result.rankedCandidates().getLast().absoluteRank();
            if (!Integer.valueOf(first).equals(result.pageStartRank())
                    || !Integer.valueOf(last).equals(result.pageEndRank())) {
                RecommendationTraceContracts.invalid("page rank boundary");
            }
            for (int index = 0; index < result.rankedCandidates().size(); index++) {
                if (result.rankedCandidates().get(index).absoluteRank() != first + index) {
                    RecommendationTraceContracts.invalid("rank discontinuity");
                }
            }
            if (result.hasNextPage() != (result.nextCursor() != null)) {
                RecommendationTraceContracts.invalid("next cursor contract");
            }
        }
        Set<String> identities = new HashSet<>();
        for (var candidate : result.rankedCandidates()) {
            String identity = candidate.entityType().wireValue() + '\0' + candidate.entityId();
            if (!identities.add(identity)) {
                RecommendationTraceContracts.invalid("duplicate page candidate identity");
            }
            if (candidate.absoluteRank() > result.finalRankedCandidateCount()) {
                RecommendationTraceContracts.invalid("candidate rank beyond final count");
            }
        }
    }
}
