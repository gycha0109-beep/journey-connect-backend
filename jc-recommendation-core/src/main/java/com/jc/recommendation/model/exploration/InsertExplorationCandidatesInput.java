package com.jc.recommendation.model.exploration;

import com.jc.recommendation.model.diversity.DiversifiedCandidate;
import com.jc.recommendation.model.score.CandidateScoreResult;
import com.jc.recommendation.policy.DiversityPolicy;
import com.jc.recommendation.policy.ExplorationPolicy;

import java.util.List;
import java.util.Objects;

public record InsertExplorationCandidatesInput(
        String rankingSnapshotId,
        String metadataSnapshotId,
        String explorationSnapshotId,
        String rankingPolicyVersion,
        String scorePolicyVersion,
        String diversityPolicyVersion,
        String explorationSeed,
        List<DiversifiedCandidate> diversifiedCandidates,
        List<CandidateScoreResult> terminalCandidates,
        List<ExplorationCandidateMetadata> explorationMetadata,
        ExplorationPolicy policy,
        DiversityPolicy diversityPolicy
) {
    public InsertExplorationCandidatesInput {
        Objects.requireNonNull(rankingSnapshotId, "rankingSnapshotId");
        Objects.requireNonNull(metadataSnapshotId, "metadataSnapshotId");
        Objects.requireNonNull(explorationSnapshotId, "explorationSnapshotId");
        Objects.requireNonNull(rankingPolicyVersion, "rankingPolicyVersion");
        Objects.requireNonNull(scorePolicyVersion, "scorePolicyVersion");
        Objects.requireNonNull(diversityPolicyVersion, "diversityPolicyVersion");
        Objects.requireNonNull(explorationSeed, "explorationSeed");
        diversifiedCandidates = List.copyOf(diversifiedCandidates);
        terminalCandidates = List.copyOf(terminalCandidates);
        explorationMetadata = List.copyOf(explorationMetadata);
    }
}
