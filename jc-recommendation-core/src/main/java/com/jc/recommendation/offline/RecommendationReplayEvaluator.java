package com.jc.recommendation.offline;

import com.jc.recommendation.exposure.RecommendationExposureEventResolver;
import com.jc.recommendation.exposure.RecommendationTraceCanonical;
import com.jc.recommendation.model.exploration.InsertedExplorationCandidate;
import com.jc.recommendation.model.exploration.PersonalizedExplorationCandidate;
import com.jc.recommendation.model.exposure.RecommendationExposureCandidate;
import com.jc.recommendation.model.exposure.RecommendationExposureDiversitySummary;
import com.jc.recommendation.model.exposure.RecommendationExposureEventV1;
import com.jc.recommendation.model.exposure.RecommendationExposureExplorationSlotDecision;
import com.jc.recommendation.model.exposure.RecommendationExposureExplorationSummary;
import com.jc.recommendation.model.exposure.RecommendationExposureOrigin;
import com.jc.recommendation.model.exposure.ResolveRecommendationExposureEventsResult;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.CollectedRankingV3Result;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.RankingV3ReplayInputSnapshot;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.RecommendationOfflineEvaluationCase;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.ReplayCoverage;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.ReplayCoverageInterval;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.ReplayMessageCode;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.ReplayMismatch;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.ReplayMismatchCategory;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.ReplayResult;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.ReplayStatus;
import com.jc.recommendation.support.StrictUtcMilliseconds;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class RecommendationReplayEvaluator {
    private static final List<ReplayMismatchCategory> CATEGORY_ORDER = List.of(
            ReplayMismatchCategory.SNAPSHOT_BINDING,
            ReplayMismatchCategory.POLICY_BINDING,
            ReplayMismatchCategory.SEED_BINDING,
            ReplayMismatchCategory.STATUS,
            ReplayMismatchCategory.COUNT,
            ReplayMismatchCategory.SUMMARY,
            ReplayMismatchCategory.PAGE_BOUNDARY,
            ReplayMismatchCategory.CANDIDATE_IDENTITY,
            ReplayMismatchCategory.CANDIDATE_RANK,
            ReplayMismatchCategory.CANDIDATE_ORIGIN,
            ReplayMismatchCategory.CANDIDATE_PROVENANCE,
            ReplayMismatchCategory.SIGNED_ZERO,
            ReplayMismatchCategory.FINGERPRINT,
            ReplayMismatchCategory.CURSOR_CHAIN
    );

    private final RecommendationExposureEventResolver exposureResolver = new RecommendationExposureEventResolver();
    private final RankingV3FullResultCollector collector = new RankingV3FullResultCollector();

    public ReplayResult evaluate(RecommendationOfflineEvaluationCase input) {
        validateCase(input);
        ResolveRecommendationExposureEventsResult resolved;
        try {
            resolved = exposureResolver.resolve(input.exposureEvents());
        } catch (RuntimeException exception) {
            return invalidResult(input.caseId(), ReplayStatus.INVALID_TRACE, input.exposureEvents().size(), null, null);
        }
        Set<String> runs = new LinkedHashSet<>();
        for (RecommendationExposureEventV1 event : resolved.resolvedEvents()) {
            runs.add(event.recommendationRunId());
        }
        if (runs.size() != 1) {
            return invalidResult(input.caseId(), ReplayStatus.INVALID_TRACE,
                    resolved.resolvedEvents().size(), null, null);
        }
        String recommendationRunId = runs.iterator().next();
        String replayKey = resolved.resolvedEvents().isEmpty() ? null : resolved.resolvedEvents().getFirst().replayKey();

        CollectedRankingV3Result collected;
        try {
            validateSnapshot(input.rankingInputSnapshot());
            collected = collector.collect(input.rankingInputSnapshot());
        } catch (RuntimeException exception) {
            return invalidResult(input.caseId(), ReplayStatus.INVALID_SNAPSHOT,
                    resolved.resolvedEvents().size(), recommendationRunId, replayKey);
        }

        List<ReplayMismatch> mismatches = new ArrayList<>();
        List<ReplayCoverageInterval> exactIntervals = new ArrayList<>();
        Set<String> mismatchedEvents = new HashSet<>();
        int exactCount = 0;
        boolean validEmpty = false;

        for (RecommendationExposureEventV1 observed : resolved.resolvedEvents()) {
            RecommendationExposureEventV1 expected;
            try {
                expected = expectedEvent(observed, input.rankingInputSnapshot(), collected);
            } catch (RuntimeException exception) {
                addMismatch(mismatches, observed.eventId(), ReplayMismatchCategory.CURSOR_CHAIN, null);
                mismatchedEvents.add(observed.eventId());
                continue;
            }
            int before = mismatches.size();
            compareEvent(observed, expected, mismatches);
            if (mismatches.size() == before) {
                exactCount++;
                if (observed.pageStartRank() != null && observed.pageEndRank() != null) {
                    exactIntervals.add(new ReplayCoverageInterval(
                            observed.pageStartRank(), observed.pageEndRank()));
                }
                if (collected.finalCandidates().isEmpty() && observed.candidates().isEmpty()) {
                    validEmpty = true;
                }
            } else {
                mismatchedEvents.add(observed.eventId());
            }
        }

        mismatches.sort(Comparator
                .comparing(ReplayMismatch::exposureEventId,
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparingInt(item -> CATEGORY_ORDER.indexOf(item.category()))
                .thenComparing(ReplayMismatch::absoluteRank,
                        Comparator.nullsFirst(Comparator.naturalOrder())));

        List<ReplayCoverageInterval> intervals = unionIntervals(exactIntervals);
        int coveredRankCount = intervals.stream()
                .mapToInt(item -> item.endRank() - item.startRank() + 1).sum();
        int totalRankCount = collected.finalCandidates().size();
        double coverageRate = totalRankCount > 0
                ? (double) coveredRankCount / totalRankCount
                : validEmpty ? 1.0 : 0.0;
        boolean fullObservation = totalRankCount > 0
                ? intervals.size() == 1
                && intervals.getFirst().startRank() == 1
                && intervals.getFirst().endRank() == totalRankCount
                : validEmpty;
        ReplayStatus status = !collected.invariantViolations().isEmpty() || !mismatches.isEmpty()
                ? ReplayStatus.MISMATCH
                : fullObservation ? ReplayStatus.EXACT_MATCH : ReplayStatus.PARTIAL_OBSERVATION;

        return new ReplayResult(
                input.caseId(), recommendationRunId, replayKey, status,
                resolved.resolvedEvents().size(), exactCount, mismatchedEvents.size(),
                new ReplayCoverage(intervals, coveredRankCount, totalRankCount, coverageRate, fullObservation),
                mismatches, collected.invariantViolations(), totalRankCount, collected.terminalCandidates().size());
    }

    private static RecommendationExposureEventV1 expectedEvent(
            RecommendationExposureEventV1 observed,
            RankingV3ReplayInputSnapshot snapshot,
            CollectedRankingV3Result collected
    ) {
        int expectedLimit = observed.requestedLimit() == null
                ? snapshot.policy().defaultResultLimit() : observed.requestedLimit();
        if (observed.effectiveLimit() != expectedLimit || expectedLimit > snapshot.policy().hardResultLimit()) {
            throw new IllegalArgumentException("invalid historical limit binding");
        }
        var first = collected.firstPage();
        int finalCount = collected.finalCandidates().size();
        List<RecommendationExposureCandidate> candidates = List.of();
        Integer pageStart = null;
        Integer pageEnd = null;
        boolean hasNext = false;
        if (observed.pageStartRank() != null) {
            if (finalCount == 0 || observed.pageStartRank() < 1 || observed.pageStartRank() > finalCount) {
                throw new IllegalArgumentException("impossible observed page start");
            }
            pageStart = observed.pageStartRank();
            pageEnd = Math.min(pageStart + observed.effectiveLimit() - 1, finalCount);
            List<RecommendationExposureCandidate> projected = new ArrayList<>();
            for (int index = pageStart - 1; index < pageEnd; index++) {
                projected.add(projectCandidate(collected.finalCandidates().get(index), index - pageStart + 2));
            }
            candidates = List.copyOf(projected);
            hasNext = pageEnd < finalCount;
        }
        RecommendationExposureDiversitySummary diversity = new RecommendationExposureDiversitySummary(
                first.diversitySummary().status(), first.diversitySummary().movedCandidateCount(),
                first.diversitySummary().maxPromotionObserved(), first.diversitySummary().maxDemotionObserved(),
                first.diversitySummary().movementBoundForcedCount(),
                first.diversitySummary().relaxationCountByDimension(),
                first.diversitySummary().violationCountByDimension(),
                first.diversitySummary().missingMetadataCountByDimension());
        List<RecommendationExposureExplorationSlotDecision> decisions = first.explorationSummary().slotDecisions()
                .stream().map(item -> new RecommendationExposureExplorationSlotDecision(
                        item.targetInsertionRank(), item.status())).toList();
        RecommendationExposureExplorationSummary exploration = new RecommendationExposureExplorationSummary(
                first.explorationSummary().status(),
                first.explorationSummary().structurallyEligibleCandidateCount(),
                first.explorationSummary().eligibleCandidateCount(),
                first.explorationSummary().insertedCandidateCount(),
                first.explorationSummary().skippedSlotCount(),
                first.explorationSummary().statusReasonRejectedCount(),
                first.explorationSummary().entityTypeRejectedCount(),
                first.explorationSummary().exposureRejectedCount(),
                first.explorationSummary().qualityEvidenceRejectedCount(),
                first.explorationSummary().qualityFloorRejectedCount(),
                first.explorationSummary().diversityGuardRejectedEvaluationCount(),
                first.explorationSummary().insertedTargetRanks(),
                first.explorationSummary().policyVersion(),
                first.explorationSummary().seedAlgorithm(), decisions);
        RecommendationExposureEventV1 base = new RecommendationExposureEventV1(
                "recommendation-exposure-v1", observed.eventId(), observed.idempotencyKey(),
                observed.recommendationRunId(), first.userId(), observed.sessionId(), first.contextId(),
                observed.surface(), observed.servedAt(), "", "", "ranking-cursor-v3",
                first.rankingSnapshotId(), first.metadataSnapshotId(), first.explorationSnapshotId(),
                first.policyVersion(), first.baseIntegrationPolicyVersion(), first.baseRankingPolicyVersion(),
                first.scorePolicyVersion(), first.componentPolicyVersions(), first.diversityPolicyVersion(),
                first.explorationPolicyVersion(), first.explorationSeed(), first.status(), first.emptyReason(),
                observed.requestedLimit(), observed.effectiveLimit(), pageStart, pageEnd, candidates.size(), hasNext,
                first.inputCount(), first.finalRankedCandidateCount(), first.terminalCandidateCount(),
                diversity, exploration, candidates);
        String expectedReplayKey = RecommendationTraceCanonical.hashCanonical(
                RecommendationTraceCanonical.replayProjection(base));
        RecommendationExposureEventV1 withReplay = replaceHashes(base, expectedReplayKey, "");
        String pageFingerprint = RecommendationTraceCanonical.hashCanonical(
                RecommendationTraceCanonical.pageProjection(withReplay));
        return replaceHashes(base, expectedReplayKey, pageFingerprint);
    }

    private static RecommendationExposureEventV1 replaceHashes(
            RecommendationExposureEventV1 value, String replayKey, String pageFingerprint
    ) {
        return new RecommendationExposureEventV1(
                value.schemaVersion(), value.eventId(), value.idempotencyKey(), value.recommendationRunId(),
                value.userId(), value.sessionId(), value.contextId(), value.surface(), value.servedAt(),
                replayKey, pageFingerprint, value.cursorVersion(), value.rankingSnapshotId(),
                value.metadataSnapshotId(), value.explorationSnapshotId(), value.rankingPolicyVersion(),
                value.baseIntegrationPolicyVersion(), value.baseRankingPolicyVersion(), value.scorePolicyVersion(),
                value.componentPolicyVersions(), value.diversityPolicyVersion(), value.explorationPolicyVersion(),
                value.explorationSeed(), value.rankingStatus(), value.rankingEmptyReason(), value.requestedLimit(),
                value.effectiveLimit(), value.pageStartRank(), value.pageEndRank(), value.pageCandidateCount(),
                value.hasNextPage(), value.inputCount(), value.finalRankedCandidateCount(),
                value.terminalCandidateCount(), value.diversitySummary(), value.explorationSummary(),
                value.candidates());
    }

    private static RecommendationExposureCandidate projectCandidate(
            com.jc.recommendation.model.exploration.ExplorationFinalCandidate candidate,
            int pagePosition
    ) {
        if (candidate instanceof PersonalizedExplorationCandidate personalized) {
            return new RecommendationExposureCandidate(
                    personalized.entityId(), personalized.entityType(), personalized.absoluteRank(), pagePosition,
                    RecommendationExposureOrigin.PERSONALIZED, personalized.score(),
                    Double.doubleToRawLongBits(personalized.score()) == Double.doubleToRawLongBits(-0.0d),
                    personalized.baseAbsoluteRank(), personalized.diversifiedAbsoluteRank(),
                    null, null, null, null, null);
        }
        InsertedExplorationCandidate exploration = (InsertedExplorationCandidate) candidate;
        return new RecommendationExposureCandidate(
                exploration.entityId(), exploration.entityType(), exploration.absoluteRank(), pagePosition,
                RecommendationExposureOrigin.EXPLORATION, null, false, null, null,
                exploration.explorationQualityScore(), exploration.recentExposureCount(),
                exploration.seededTieBreakKey(), exploration.explorationPoolRank(),
                exploration.targetInsertionRank());
    }

    private static void compareEvent(
            RecommendationExposureEventV1 observed,
            RecommendationExposureEventV1 expected,
            List<ReplayMismatch> mismatches
    ) {
        if (!observed.rankingSnapshotId().equals(expected.rankingSnapshotId())
                || !observed.metadataSnapshotId().equals(expected.metadataSnapshotId())
                || !observed.explorationSnapshotId().equals(expected.explorationSnapshotId())
                || !observed.userId().equals(expected.userId())
                || !observed.contextId().equals(expected.contextId())) {
            addMismatch(mismatches, observed.eventId(), ReplayMismatchCategory.SNAPSHOT_BINDING, null);
        }
        if (!observed.cursorVersion().equals(expected.cursorVersion())
                || !observed.rankingPolicyVersion().equals(expected.rankingPolicyVersion())
                || !observed.baseIntegrationPolicyVersion().equals(expected.baseIntegrationPolicyVersion())
                || !observed.baseRankingPolicyVersion().equals(expected.baseRankingPolicyVersion())
                || !observed.scorePolicyVersion().equals(expected.scorePolicyVersion())
                || !observed.componentPolicyVersions().equals(expected.componentPolicyVersions())
                || !observed.diversityPolicyVersion().equals(expected.diversityPolicyVersion())
                || !observed.explorationPolicyVersion().equals(expected.explorationPolicyVersion())) {
            addMismatch(mismatches, observed.eventId(), ReplayMismatchCategory.POLICY_BINDING, null);
        }
        if (!observed.explorationSeed().equals(expected.explorationSeed())) {
            addMismatch(mismatches, observed.eventId(), ReplayMismatchCategory.SEED_BINDING, null);
        }
        if (observed.rankingStatus() != expected.rankingStatus()
                || observed.rankingEmptyReason() != expected.rankingEmptyReason()) {
            addMismatch(mismatches, observed.eventId(), ReplayMismatchCategory.STATUS, null);
        }
        if (observed.inputCount() != expected.inputCount()
                || observed.finalRankedCandidateCount() != expected.finalRankedCandidateCount()
                || observed.terminalCandidateCount() != expected.terminalCandidateCount()
                || observed.pageCandidateCount() != expected.pageCandidateCount()) {
            addMismatch(mismatches, observed.eventId(), ReplayMismatchCategory.COUNT, null);
        }
        if (!observed.diversitySummary().equals(expected.diversitySummary())
                || !observed.explorationSummary().equals(expected.explorationSummary())) {
            addMismatch(mismatches, observed.eventId(), ReplayMismatchCategory.SUMMARY, null);
        }
        if (!Objects.equals(observed.pageStartRank(), expected.pageStartRank())
                || !Objects.equals(observed.pageEndRank(), expected.pageEndRank())
                || observed.hasNextPage() != expected.hasNextPage()) {
            addMismatch(mismatches, observed.eventId(), ReplayMismatchCategory.PAGE_BOUNDARY, null);
        }
        if (observed.candidates().size() != expected.candidates().size()) {
            addMismatch(mismatches, observed.eventId(), ReplayMismatchCategory.COUNT, null);
        }
        int maximum = Math.max(observed.candidates().size(), expected.candidates().size());
        for (int index = 0; index < maximum; index++) {
            RecommendationExposureCandidate actual = index < observed.candidates().size()
                    ? observed.candidates().get(index) : null;
            RecommendationExposureCandidate projected = index < expected.candidates().size()
                    ? expected.candidates().get(index) : null;
            Integer rank = actual != null ? actual.absoluteRank()
                    : projected != null ? projected.absoluteRank() : null;
            if (actual == null || projected == null) {
                addMismatch(mismatches, observed.eventId(), ReplayMismatchCategory.CANDIDATE_IDENTITY, rank);
                continue;
            }
            if (actual.entityType() != projected.entityType() || !actual.entityId().equals(projected.entityId())) {
                addMismatch(mismatches, observed.eventId(), ReplayMismatchCategory.CANDIDATE_IDENTITY, rank);
            }
            if (actual.absoluteRank() != projected.absoluteRank()
                    || actual.pagePosition() != projected.pagePosition()) {
                addMismatch(mismatches, observed.eventId(), ReplayMismatchCategory.CANDIDATE_RANK, rank);
            }
            if (actual.origin() != projected.origin()) {
                addMismatch(mismatches, observed.eventId(), ReplayMismatchCategory.CANDIDATE_ORIGIN, rank);
            }
            if (!numericEqual(actual.score(), projected.score())
                    || !Objects.equals(actual.baseAbsoluteRank(), projected.baseAbsoluteRank())
                    || !Objects.equals(actual.diversifiedAbsoluteRank(), projected.diversifiedAbsoluteRank())
                    || !numericEqual(actual.explorationQualityScore(), projected.explorationQualityScore())
                    || !Objects.equals(actual.recentExposureCount(), projected.recentExposureCount())
                    || !Objects.equals(actual.seededTieBreakKey(), projected.seededTieBreakKey())
                    || !Objects.equals(actual.explorationPoolRank(), projected.explorationPoolRank())
                    || !Objects.equals(actual.targetInsertionRank(), projected.targetInsertionRank())) {
                addMismatch(mismatches, observed.eventId(), ReplayMismatchCategory.CANDIDATE_PROVENANCE, rank);
            }
            if (actual.scoreIsNegativeZero() != projected.scoreIsNegativeZero()) {
                addMismatch(mismatches, observed.eventId(), ReplayMismatchCategory.SIGNED_ZERO, rank);
            }
        }
        if (!observed.pageFingerprint().equals(expected.pageFingerprint())) {
            addMismatch(mismatches, observed.eventId(), ReplayMismatchCategory.FINGERPRINT, null);
        }
    }

    private static boolean numericEqual(Double a, Double b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.doubleValue() == b.doubleValue();
    }

    private static void addMismatch(
            List<ReplayMismatch> target,
            String eventId,
            ReplayMismatchCategory category,
            Integer absoluteRank
    ) {
        boolean exists = target.stream().anyMatch(item -> Objects.equals(item.exposureEventId(), eventId)
                && item.category() == category && Objects.equals(item.absoluteRank(), absoluteRank));
        if (!exists) {
            target.add(new ReplayMismatch(eventId, category, absoluteRank, code(category)));
        }
    }

    private static ReplayMessageCode code(ReplayMismatchCategory category) {
        return switch (category) {
            case SNAPSHOT_BINDING -> ReplayMessageCode.SNAPSHOT_BINDING_MISMATCH;
            case POLICY_BINDING -> ReplayMessageCode.POLICY_BINDING_MISMATCH;
            case SEED_BINDING -> ReplayMessageCode.SEED_BINDING_MISMATCH;
            case STATUS -> ReplayMessageCode.STATUS_MISMATCH;
            case COUNT -> ReplayMessageCode.COUNT_MISMATCH;
            case SUMMARY -> ReplayMessageCode.SUMMARY_MISMATCH;
            case PAGE_BOUNDARY -> ReplayMessageCode.PAGE_BOUNDARY_MISMATCH;
            case CANDIDATE_IDENTITY -> ReplayMessageCode.CANDIDATE_IDENTITY_MISMATCH;
            case CANDIDATE_RANK -> ReplayMessageCode.CANDIDATE_RANK_MISMATCH;
            case CANDIDATE_ORIGIN -> ReplayMessageCode.CANDIDATE_ORIGIN_MISMATCH;
            case CANDIDATE_PROVENANCE -> ReplayMessageCode.CANDIDATE_PROVENANCE_MISMATCH;
            case SIGNED_ZERO -> ReplayMessageCode.SIGNED_ZERO_MISMATCH;
            case FINGERPRINT -> ReplayMessageCode.FINGERPRINT_MISMATCH;
            case CURSOR_CHAIN -> ReplayMessageCode.CURSOR_CHAIN_MISMATCH;
        };
    }

    private static List<ReplayCoverageInterval> unionIntervals(List<ReplayCoverageInterval> intervals) {
        List<ReplayCoverageInterval> sorted = intervals.stream()
                .sorted(Comparator.comparingInt(ReplayCoverageInterval::startRank)
                        .thenComparingInt(ReplayCoverageInterval::endRank)).toList();
        List<ReplayCoverageInterval> merged = new ArrayList<>();
        for (ReplayCoverageInterval interval : sorted) {
            if (!merged.isEmpty() && interval.startRank() <= merged.getLast().endRank() + 1) {
                ReplayCoverageInterval last = merged.getLast();
                merged.set(merged.size() - 1, new ReplayCoverageInterval(
                        last.startRank(), Math.max(last.endRank(), interval.endRank())));
            } else {
                merged.add(interval);
            }
        }
        return List.copyOf(merged);
    }

    private static ReplayResult invalidResult(
            String caseId,
            ReplayStatus status,
            int observed,
            String recommendationRunId,
            String replayKey
    ) {
        return new ReplayResult(caseId, recommendationRunId, replayKey, status, observed, 0, 0,
                new ReplayCoverage(List.of(), 0, 0, 0.0, false), List.of(), List.of(), null, null);
    }

    private static void validateCase(RecommendationOfflineEvaluationCase value) {
        if (value.caseId().isBlank()) {
            throw new IllegalArgumentException("INVALID_REPLAY_CASE: caseId");
        }
        StrictUtcMilliseconds.parseEpochMilli(value.evaluationCutoffAt(), "evaluationCutoffAt");
        if (value.exposureEvents().isEmpty()) {
            throw new IllegalArgumentException("INVALID_REPLAY_CASE: exposureEvents must not be empty");
        }
    }

    private static void validateSnapshot(RankingV3ReplayInputSnapshot value) {
        if (value.rankingSnapshotId().isBlank() || value.metadataSnapshotId().isBlank()
                || value.explorationSnapshotId().isBlank() || value.userId().isBlank()
                || value.contextId().isBlank() || value.scorePolicyVersion().isBlank()
                || value.explorationSeed().isBlank()) {
            throw new IllegalArgumentException("INVALID_REPLAY_SNAPSHOT: nonblank binding");
        }
        int seedBytes = value.explorationSeed().getBytes(StandardCharsets.UTF_8).length;
        if (seedBytes < 1 || seedBytes > 128 || value.candidates().size() > 100) {
            throw new IllegalArgumentException("INVALID_REPLAY_SNAPSHOT: limit");
        }
    }
}
