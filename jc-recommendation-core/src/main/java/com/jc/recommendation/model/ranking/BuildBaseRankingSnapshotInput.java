package com.jc.recommendation.model.ranking;

import com.jc.recommendation.model.score.CandidateScoreResult;
import com.jc.recommendation.model.score.ScoreComponentPolicyVersions;

import java.util.List;
import java.util.Objects;

public record BuildBaseRankingSnapshotInput(
        String userId,
        String contextId,
        String scorePolicyVersion,
        ScoreComponentPolicyVersions componentPolicyVersions,
        List<CandidateScoreResult> candidates
) {
    public BuildBaseRankingSnapshotInput {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(contextId, "contextId");
        Objects.requireNonNull(scorePolicyVersion, "scorePolicyVersion");
        Objects.requireNonNull(componentPolicyVersions, "componentPolicyVersions");
        candidates = List.copyOf(candidates);
    }
}
