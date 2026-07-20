package com.jc.recommendation.model.ranking;

import com.jc.recommendation.model.score.ScoreComponentPolicyVersions;

import java.util.List;
import java.util.Objects;

public record BaseRankingSnapshot(
        ScoreComponentPolicyVersions componentPolicyVersions,
        int inputCount,
        int scoredCandidateCount,
        int terminalCandidateCount,
        List<RankedCandidate> rankedCandidates,
        List<TerminalCandidateAudit> terminalCandidates
) {
    public BaseRankingSnapshot {
        Objects.requireNonNull(componentPolicyVersions, "componentPolicyVersions");
        rankedCandidates = List.copyOf(rankedCandidates);
        terminalCandidates = List.copyOf(terminalCandidates);
    }
}
