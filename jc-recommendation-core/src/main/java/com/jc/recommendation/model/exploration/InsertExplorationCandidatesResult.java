package com.jc.recommendation.model.exploration;

import com.jc.recommendation.model.score.CandidateScoreResult;

import java.util.List;
import java.util.Objects;

public record InsertExplorationCandidatesResult(
        String rankingSnapshotId,
        String metadataSnapshotId,
        String explorationSnapshotId,
        String rankingPolicyVersion,
        String scorePolicyVersion,
        String diversityPolicyVersion,
        String explorationPolicyVersion,
        String explorationSeed,
        int inputPersonalizedCount,
        int inputTerminalCount,
        int eligibleCandidateCount,
        int insertedCandidateCount,
        int outputCount,
        int remainingTerminalCount,
        ExplorationSummary summary,
        List<ExplorationFinalCandidate> finalCandidates,
        List<CandidateScoreResult> remainingTerminalCandidates
) {
    public InsertExplorationCandidatesResult {
        Objects.requireNonNull(rankingSnapshotId, "rankingSnapshotId");
        Objects.requireNonNull(metadataSnapshotId, "metadataSnapshotId");
        Objects.requireNonNull(explorationSnapshotId, "explorationSnapshotId");
        Objects.requireNonNull(rankingPolicyVersion, "rankingPolicyVersion");
        Objects.requireNonNull(scorePolicyVersion, "scorePolicyVersion");
        Objects.requireNonNull(diversityPolicyVersion, "diversityPolicyVersion");
        Objects.requireNonNull(explorationPolicyVersion, "explorationPolicyVersion");
        Objects.requireNonNull(explorationSeed, "explorationSeed");
        Objects.requireNonNull(summary, "summary");
        finalCandidates = List.copyOf(finalCandidates);
        remainingTerminalCandidates = List.copyOf(remainingTerminalCandidates);
    }
}
