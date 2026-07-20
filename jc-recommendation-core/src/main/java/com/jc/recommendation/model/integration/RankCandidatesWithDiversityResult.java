package com.jc.recommendation.model.integration;

import com.jc.recommendation.model.ranking.RankingEmptyReason;
import com.jc.recommendation.model.ranking.RankingResultStatus;
import com.jc.recommendation.model.ranking.TerminalCandidateAudit;
import com.jc.recommendation.model.score.ScoreComponentPolicyVersions;

import java.util.List;
import java.util.Objects;

public record RankCandidatesWithDiversityResult(
        String rankingSnapshotId,
        String metadataSnapshotId,
        String userId,
        String contextId,
        RankingResultStatus status,
        RankingEmptyReason emptyReason,
        String policyVersion,
        String baseRankingPolicyVersion,
        String scorePolicyVersion,
        ScoreComponentPolicyVersions componentPolicyVersions,
        String diversityPolicyVersion,
        int inputCount,
        int scoredCandidateCount,
        int terminalCandidateCount,
        Integer requestedLimit,
        int effectiveLimit,
        Integer pageStartRank,
        Integer pageEndRank,
        boolean hasNextPage,
        String nextCursor,
        DiversityRankingSummary diversitySummary,
        List<DiversityRankedCandidate> rankedCandidates,
        List<TerminalCandidateAudit> terminalCandidates
) {
    public RankCandidatesWithDiversityResult {
        Objects.requireNonNull(rankingSnapshotId, "rankingSnapshotId");
        Objects.requireNonNull(metadataSnapshotId, "metadataSnapshotId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(contextId, "contextId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(policyVersion, "policyVersion");
        Objects.requireNonNull(baseRankingPolicyVersion, "baseRankingPolicyVersion");
        Objects.requireNonNull(scorePolicyVersion, "scorePolicyVersion");
        Objects.requireNonNull(componentPolicyVersions, "componentPolicyVersions");
        Objects.requireNonNull(diversityPolicyVersion, "diversityPolicyVersion");
        Objects.requireNonNull(diversitySummary, "diversitySummary");
        rankedCandidates = List.copyOf(rankedCandidates);
        terminalCandidates = List.copyOf(terminalCandidates);
    }
}
