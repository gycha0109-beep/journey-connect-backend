package com.jc.recommendation.model.ranking;

import com.jc.recommendation.model.score.ScoreComponentPolicyVersions;

import java.util.List;
import java.util.Objects;

public record RankCandidatesResult(
        String rankingSnapshotId,
        String userId,
        String contextId,
        RankingResultStatus status,
        RankingEmptyReason emptyReason,
        String policyVersion,
        String scorePolicyVersion,
        ScoreComponentPolicyVersions componentPolicyVersions,
        int inputCount,
        int scoredCandidateCount,
        int terminalCandidateCount,
        Integer requestedLimit,
        int effectiveLimit,
        Integer pageStartRank,
        Integer pageEndRank,
        boolean hasNextPage,
        String nextCursor,
        List<RankedCandidate> rankedCandidates,
        List<TerminalCandidateAudit> terminalCandidates
) {
    public RankCandidatesResult {
        Objects.requireNonNull(rankingSnapshotId, "rankingSnapshotId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(contextId, "contextId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(policyVersion, "policyVersion");
        Objects.requireNonNull(scorePolicyVersion, "scorePolicyVersion");
        Objects.requireNonNull(componentPolicyVersions, "componentPolicyVersions");
        rankedCandidates = List.copyOf(rankedCandidates);
        terminalCandidates = List.copyOf(terminalCandidates);
    }
}
