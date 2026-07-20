package com.jc.recommendation.model.ranking;

import com.jc.recommendation.model.score.CandidateScoreResult;
import com.jc.recommendation.model.score.ScoreComponentPolicyVersions;
import com.jc.recommendation.policy.RankingPolicy;

import java.util.List;
import java.util.Objects;

public record RankCandidatesInput(
        String rankingSnapshotId,
        String userId,
        String contextId,
        String scorePolicyVersion,
        ScoreComponentPolicyVersions componentPolicyVersions,
        List<CandidateScoreResult> candidates,
        Integer resultLimit,
        String cursor,
        RankingPolicy policy
) {
    public RankCandidatesInput {
        Objects.requireNonNull(rankingSnapshotId, "rankingSnapshotId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(contextId, "contextId");
        Objects.requireNonNull(scorePolicyVersion, "scorePolicyVersion");
        Objects.requireNonNull(componentPolicyVersions, "componentPolicyVersions");
        candidates = List.copyOf(candidates);
    }
}
