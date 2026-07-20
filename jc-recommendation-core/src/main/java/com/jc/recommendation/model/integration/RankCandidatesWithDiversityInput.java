package com.jc.recommendation.model.integration;

import com.jc.recommendation.model.diversity.DiversityCandidateMetadata;
import com.jc.recommendation.model.score.CandidateScoreResult;
import com.jc.recommendation.model.score.ScoreComponentPolicyVersions;
import com.jc.recommendation.policy.DiversityEnabledRankingPolicy;
import com.jc.recommendation.policy.DiversityPolicy;

import java.util.List;
import java.util.Objects;

public record RankCandidatesWithDiversityInput(
        String rankingSnapshotId,
        String metadataSnapshotId,
        String userId,
        String contextId,
        String scorePolicyVersion,
        ScoreComponentPolicyVersions componentPolicyVersions,
        List<CandidateScoreResult> candidates,
        List<DiversityCandidateMetadata> candidateMetadata,
        Integer resultLimit,
        String cursor,
        DiversityEnabledRankingPolicy policy,
        DiversityPolicy diversityPolicy
) {
    public RankCandidatesWithDiversityInput {
        Objects.requireNonNull(rankingSnapshotId, "rankingSnapshotId");
        Objects.requireNonNull(metadataSnapshotId, "metadataSnapshotId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(contextId, "contextId");
        Objects.requireNonNull(scorePolicyVersion, "scorePolicyVersion");
        Objects.requireNonNull(componentPolicyVersions, "componentPolicyVersions");
        candidates = List.copyOf(candidates);
        candidateMetadata = List.copyOf(candidateMetadata);
    }
}
