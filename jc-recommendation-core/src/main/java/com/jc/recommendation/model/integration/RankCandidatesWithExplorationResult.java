package com.jc.recommendation.model.integration;

import com.jc.recommendation.model.exploration.ExplorationFinalCandidate;
import com.jc.recommendation.model.exploration.ExplorationSummary;
import com.jc.recommendation.model.ranking.RankingEmptyReason;
import com.jc.recommendation.model.ranking.RankingResultStatus;
import com.jc.recommendation.model.ranking.TerminalCandidateAudit;
import com.jc.recommendation.model.score.ScoreComponentPolicyVersions;

import java.util.List;
import java.util.Objects;

public record RankCandidatesWithExplorationResult(
        String rankingSnapshotId,
        String metadataSnapshotId,
        String explorationSnapshotId,
        String userId,
        String contextId,
        RankingResultStatus status,
        RankingEmptyReason emptyReason,
        String policyVersion,
        String baseIntegrationPolicyVersion,
        String baseRankingPolicyVersion,
        String scorePolicyVersion,
        ScoreComponentPolicyVersions componentPolicyVersions,
        String diversityPolicyVersion,
        String explorationPolicyVersion,
        String explorationSeed,
        int inputCount,
        int scoredCandidateCount,
        int sourceTerminalCandidateCount,
        int personalizedCandidateCount,
        int structurallyEligibleExplorationCandidateCount,
        int explorationEligibleCandidateCount,
        int explorationInsertedCandidateCount,
        int finalRankedCandidateCount,
        int terminalCandidateCount,
        Integer requestedLimit,
        int effectiveLimit,
        Integer pageStartRank,
        Integer pageEndRank,
        boolean hasNextPage,
        String nextCursor,
        DiversityRankingSummary diversitySummary,
        ExplorationSummary explorationSummary,
        List<ExplorationFinalCandidate> rankedCandidates,
        List<TerminalCandidateAudit> terminalCandidates
) {
    public RankCandidatesWithExplorationResult {
        Objects.requireNonNull(rankingSnapshotId, "rankingSnapshotId");
        Objects.requireNonNull(metadataSnapshotId, "metadataSnapshotId");
        Objects.requireNonNull(explorationSnapshotId, "explorationSnapshotId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(contextId, "contextId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(policyVersion, "policyVersion");
        Objects.requireNonNull(baseIntegrationPolicyVersion, "baseIntegrationPolicyVersion");
        Objects.requireNonNull(baseRankingPolicyVersion, "baseRankingPolicyVersion");
        Objects.requireNonNull(scorePolicyVersion, "scorePolicyVersion");
        Objects.requireNonNull(componentPolicyVersions, "componentPolicyVersions");
        Objects.requireNonNull(diversityPolicyVersion, "diversityPolicyVersion");
        Objects.requireNonNull(explorationPolicyVersion, "explorationPolicyVersion");
        Objects.requireNonNull(explorationSeed, "explorationSeed");
        Objects.requireNonNull(diversitySummary, "diversitySummary");
        Objects.requireNonNull(explorationSummary, "explorationSummary");
        rankedCandidates = List.copyOf(rankedCandidates);
        terminalCandidates = List.copyOf(terminalCandidates);
    }
}
