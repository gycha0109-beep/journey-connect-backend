package com.jc.recommendation.offline;

import com.jc.recommendation.integration.ExplorationEnabledRanker;
import com.jc.recommendation.model.exploration.ExplorationCandidateOrigin;
import com.jc.recommendation.model.exploration.ExplorationFinalCandidate;
import com.jc.recommendation.model.exploration.InsertedExplorationCandidate;
import com.jc.recommendation.model.exploration.PersonalizedExplorationCandidate;
import com.jc.recommendation.model.integration.RankCandidatesWithExplorationInput;
import com.jc.recommendation.model.integration.RankCandidatesWithExplorationResult;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.CollectedRankingV3Result;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.InvariantViolationCode;
import com.jc.recommendation.model.offline.OfflineEvaluationContracts.RankingV3ReplayInputSnapshot;
import com.jc.recommendation.model.ranking.TerminalCandidateAudit;
import com.jc.recommendation.model.score.CandidateScoreResult;
import com.jc.recommendation.model.score.CandidateScoreStatus;
import com.jc.recommendation.policy.OfflineEvaluationPolicies;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RankingV3FullResultCollector {
    private final ExplorationEnabledRanker ranker = new ExplorationEnabledRanker();

    public CollectedRankingV3Result collect(RankingV3ReplayInputSnapshot snapshot) {
        List<RankCandidatesWithExplorationResult> pages = new ArrayList<>();
        List<ExplorationFinalCandidate> finalCandidates = new ArrayList<>();
        Set<String> seenCursors = new HashSet<>();
        LinkedHashSet<InvariantViolationCode> violations = new LinkedHashSet<>();
        String cursor = null;
        RankCandidatesWithExplorationResult first = null;

        for (int call = 0; call < OfflineEvaluationPolicies.V1.maximumCollectorPageCount(); call++) {
            RankCandidatesWithExplorationResult result = ranker.rank(new RankCandidatesWithExplorationInput(
                    snapshot.rankingSnapshotId(), snapshot.metadataSnapshotId(), snapshot.explorationSnapshotId(),
                    snapshot.userId(), snapshot.contextId(), snapshot.scorePolicyVersion(),
                    snapshot.componentPolicyVersions(), snapshot.explorationSeed(), snapshot.candidates(),
                    snapshot.candidateMetadata(), snapshot.explorationMetadata(),
                    OfflineEvaluationPolicies.V1.evaluatorPageSize(), cursor,
                    snapshot.policy(), snapshot.diversityPolicy(), snapshot.explorationPolicy()
            ));
            if (first == null) {
                first = result;
            } else {
                if (!sameStaticResult(result, first)) {
                    violations.add(InvariantViolationCode.COLLECTOR_BINDING_DRIFT);
                }
                if (result.status() != first.status() || result.emptyReason() != first.emptyReason()) {
                    violations.add(InvariantViolationCode.COLLECTOR_STATUS_DRIFT);
                }
                if (!result.diversitySummary().equals(first.diversitySummary())
                        || !result.explorationSummary().equals(first.explorationSummary())) {
                    violations.add(InvariantViolationCode.SUMMARY_DRIFT);
                }
                if (!result.terminalCandidates().equals(first.terminalCandidates())) {
                    violations.add(InvariantViolationCode.COLLECTOR_TERMINAL_AUDIT_DRIFT);
                }
            }

            int expectedStart = finalCandidates.size() + 1;
            if (!result.rankedCandidates().isEmpty()) {
                int expectedEnd = expectedStart + result.rankedCandidates().size() - 1;
                if (!Integer.valueOf(expectedStart).equals(result.pageStartRank())
                        || !Integer.valueOf(expectedEnd).equals(result.pageEndRank())) {
                    violations.add(InvariantViolationCode.COLLECTOR_PAGE_BOUNDARY_DISCONTINUITY);
                }
            } else if (result.pageStartRank() != null || result.pageEndRank() != null) {
                violations.add(InvariantViolationCode.COLLECTOR_PAGE_BOUNDARY_DISCONTINUITY);
            }

            pages.add(result);
            finalCandidates.addAll(result.rankedCandidates());

            if (!result.hasNextPage()) {
                if (result.nextCursor() != null) {
                    violations.add(InvariantViolationCode.COLLECTOR_CURSOR_DISCONTINUITY);
                }
                break;
            }
            if (result.nextCursor() == null) {
                violations.add(InvariantViolationCode.COLLECTOR_CURSOR_DISCONTINUITY);
                break;
            }
            if (!seenCursors.add(result.nextCursor())) {
                violations.add(InvariantViolationCode.COLLECTOR_CURSOR_LOOP);
                break;
            }
            cursor = result.nextCursor();
            if (call == OfflineEvaluationPolicies.V1.maximumCollectorPageCount() - 1) {
                violations.add(InvariantViolationCode.COLLECTOR_PAGE_LIMIT_EXCEEDED);
            }
        }

        if (first == null) {
            throw new IllegalStateException("collector produced no page");
        }
        validateFinalPartition(snapshot, first, finalCandidates, violations);
        return new CollectedRankingV3Result(
                pages, finalCandidates, first.terminalCandidates(), first, List.copyOf(violations));
    }

    private static boolean sameStaticResult(
            RankCandidatesWithExplorationResult a,
            RankCandidatesWithExplorationResult b
    ) {
        return a.rankingSnapshotId().equals(b.rankingSnapshotId())
                && a.metadataSnapshotId().equals(b.metadataSnapshotId())
                && a.explorationSnapshotId().equals(b.explorationSnapshotId())
                && a.userId().equals(b.userId())
                && a.contextId().equals(b.contextId())
                && a.policyVersion().equals(b.policyVersion())
                && a.baseIntegrationPolicyVersion().equals(b.baseIntegrationPolicyVersion())
                && a.baseRankingPolicyVersion().equals(b.baseRankingPolicyVersion())
                && a.scorePolicyVersion().equals(b.scorePolicyVersion())
                && a.componentPolicyVersions().equals(b.componentPolicyVersions())
                && a.diversityPolicyVersion().equals(b.diversityPolicyVersion())
                && a.explorationPolicyVersion().equals(b.explorationPolicyVersion())
                && a.explorationSeed().equals(b.explorationSeed())
                && a.inputCount() == b.inputCount()
                && a.scoredCandidateCount() == b.scoredCandidateCount()
                && a.sourceTerminalCandidateCount() == b.sourceTerminalCandidateCount()
                && a.personalizedCandidateCount() == b.personalizedCandidateCount()
                && a.structurallyEligibleExplorationCandidateCount() == b.structurallyEligibleExplorationCandidateCount()
                && a.explorationEligibleCandidateCount() == b.explorationEligibleCandidateCount()
                && a.explorationInsertedCandidateCount() == b.explorationInsertedCandidateCount()
                && a.finalRankedCandidateCount() == b.finalRankedCandidateCount()
                && a.terminalCandidateCount() == b.terminalCandidateCount();
    }

    private static void validateFinalPartition(
            RankingV3ReplayInputSnapshot snapshot,
            RankCandidatesWithExplorationResult first,
            List<ExplorationFinalCandidate> finalCandidates,
            Set<InvariantViolationCode> violations
    ) {
        Map<String, CandidateScoreResult> sourceByIdentity = new HashMap<>();
        for (CandidateScoreResult candidate : snapshot.candidates()) {
            sourceByIdentity.put(identity(candidate.entityType().wireValue(), candidate.entityId()), candidate);
        }
        Set<String> finalIdentities = new HashSet<>();
        for (int index = 0; index < finalCandidates.size(); index++) {
            ExplorationFinalCandidate candidate = finalCandidates.get(index);
            if (candidate.absoluteRank() != index + 1) {
                violations.add(InvariantViolationCode.ABSOLUTE_RANK_DISCONTINUITY);
            }
            String identity = identity(candidate.entityType().wireValue(), candidate.entityId());
            if (!finalIdentities.add(identity)) {
                violations.add(InvariantViolationCode.DUPLICATE_FINAL_IDENTITY);
            }
            CandidateScoreResult source = sourceByIdentity.get(identity);
            if (source == null) {
                violations.add(InvariantViolationCode.SYNTHETIC_FINAL_IDENTITY);
                continue;
            }
            if (candidate.origin() == ExplorationCandidateOrigin.PERSONALIZED) {
                if (source.status() != CandidateScoreStatus.SCORED) {
                    violations.add(InvariantViolationCode.INPUT_PARTITION_MISMATCH);
                } else if (candidate instanceof PersonalizedExplorationCandidate personalized) {
                    if (personalized.score() == source.score()
                            && Double.doubleToRawLongBits(personalized.score())
                            != Double.doubleToRawLongBits(source.score())) {
                        violations.add(InvariantViolationCode.PERSONALIZED_SIGNED_ZERO_MUTATION);
                    } else if (Double.doubleToRawLongBits(personalized.score())
                            != Double.doubleToRawLongBits(source.score())) {
                        violations.add(InvariantViolationCode.PERSONALIZED_SCORE_MUTATION);
                    }
                }
            } else if (candidate instanceof InsertedExplorationCandidate inserted) {
                if (inserted.score() != null) {
                    violations.add(InvariantViolationCode.EXPLORATION_SCORE_NON_NULL);
                }
                if (source.status() != CandidateScoreStatus.NOT_APPLICABLE
                        || source.notApplicableReason() == null
                        || !"no_anchor_component".equals(source.notApplicableReason().wireValue())) {
                    violations.add(InvariantViolationCode.INPUT_PARTITION_MISMATCH);
                }
            }
        }

        Set<String> terminalIdentities = new HashSet<>();
        for (TerminalCandidateAudit terminal : first.terminalCandidates()) {
            String identity = identity(terminal.entityType().wireValue(), terminal.entityId());
            if (!sourceByIdentity.containsKey(identity)) {
                violations.add(InvariantViolationCode.SYNTHETIC_FINAL_IDENTITY);
            }
            if (finalIdentities.contains(identity) || !terminalIdentities.add(identity)) {
                violations.add(InvariantViolationCode.INPUT_PARTITION_MISMATCH);
            }
        }
        if (sourceByIdentity.size() != finalIdentities.size() + terminalIdentities.size()
                || first.inputCount() != first.finalRankedCandidateCount() + first.terminalCandidateCount()) {
            violations.add(InvariantViolationCode.INPUT_PARTITION_MISMATCH);
        }
        if (finalCandidates.size() != first.finalRankedCandidateCount()) {
            violations.add(InvariantViolationCode.ABSOLUTE_RANK_DISCONTINUITY);
        }
    }

    private static String identity(String entityType, String entityId) {
        return entityType + '\0' + entityId;
    }
}
