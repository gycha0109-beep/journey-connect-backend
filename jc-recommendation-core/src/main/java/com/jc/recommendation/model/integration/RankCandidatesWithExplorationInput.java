package com.jc.recommendation.model.integration;

import com.jc.recommendation.model.diversity.DiversityCandidateMetadata;
import com.jc.recommendation.model.exploration.ExplorationCandidateMetadata;
import com.jc.recommendation.model.score.CandidateScoreResult;
import com.jc.recommendation.model.score.ScoreComponentPolicyVersions;
import com.jc.recommendation.policy.DiversityPolicy;
import com.jc.recommendation.policy.ExplorationEnabledRankingPolicy;
import com.jc.recommendation.policy.ExplorationPolicy;

import java.util.List;
import java.util.Objects;

public record RankCandidatesWithExplorationInput(
        String rankingSnapshotId,
        String metadataSnapshotId,
        String explorationSnapshotId,
        String userId,
        String contextId,
        String scorePolicyVersion,
        ScoreComponentPolicyVersions componentPolicyVersions,
        String explorationSeed,
        List<CandidateScoreResult> candidates,
        List<DiversityCandidateMetadata> candidateMetadata,
        List<ExplorationCandidateMetadata> explorationMetadata,
        Integer resultLimit,
        String cursor,
        ExplorationEnabledRankingPolicy policy,
        DiversityPolicy diversityPolicy,
        ExplorationPolicy explorationPolicy
) {
    public RankCandidatesWithExplorationInput {
        Objects.requireNonNull(rankingSnapshotId, "rankingSnapshotId");
        Objects.requireNonNull(metadataSnapshotId, "metadataSnapshotId");
        Objects.requireNonNull(explorationSnapshotId, "explorationSnapshotId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(contextId, "contextId");
        Objects.requireNonNull(scorePolicyVersion, "scorePolicyVersion");
        Objects.requireNonNull(componentPolicyVersions, "componentPolicyVersions");
        Objects.requireNonNull(explorationSeed, "explorationSeed");
        candidates = List.copyOf(candidates);
        candidateMetadata = List.copyOf(candidateMetadata);
        explorationMetadata = List.copyOf(explorationMetadata);
    }
}
