package com.jc.recommendation.ranking;

import com.jc.recommendation.model.ranking.BaseRankingSnapshot;
import com.jc.recommendation.model.ranking.BuildBaseRankingSnapshotInput;
import com.jc.recommendation.model.ranking.RankedCandidate;
import com.jc.recommendation.model.ranking.RankingSortKey;
import com.jc.recommendation.model.ranking.TerminalCandidateAudit;
import com.jc.recommendation.model.score.CandidateScoreResult;
import com.jc.recommendation.model.score.CandidateScoreStatus;
import com.jc.recommendation.model.score.ScoreComponentPolicyVersions;
import com.jc.recommendation.policy.RankingPolicies;
import com.jc.recommendation.policy.RankingPolicy;
import com.jc.recommendation.support.Utf16CodeUnitComparator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class BaseRankingSnapshotBuilder {
    private record ValidScoredCandidate(
            CandidateScoreResult source,
            double comparisonScore,
            int entityTypeRank
    ) {
    }

    public BaseRankingSnapshot build(BuildBaseRankingSnapshotInput input) {
        return build(input, RankingPolicies.V1);
    }

    public BaseRankingSnapshot build(BuildBaseRankingSnapshotInput input, RankingPolicy policy) {
        RankingContracts.nonBlank(input.userId(), "userId");
        RankingContracts.nonBlank(input.contextId(), "contextId");
        RankingContracts.nonBlank(input.scorePolicyVersion(), "scorePolicyVersion");
        RankingContracts.validatePolicy(policy);
        if (!input.scorePolicyVersion().equals(policy.expectedScorePolicyVersion())) {
            throw new IllegalArgumentException("scorePolicyVersion does not match RankingPolicy expectation");
        }
        if (input.candidates().size() > policy.maxInputCandidates()) {
            throw new IllegalArgumentException("candidate count exceeds maxInputCandidates");
        }

        List<CandidateScoreResult> candidates = input.candidates();
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < candidates.size(); index++) {
            CandidateScoreResult candidate = candidates.get(index);
            RankingContracts.validateCandidate(candidate, index);
            if (!candidate.userId().equals(input.userId())) {
                throw new IllegalArgumentException("candidate userId mismatch");
            }
            if (!candidate.contextId().equals(input.contextId())) {
                throw new IllegalArgumentException("candidate contextId mismatch");
            }
            if (!candidate.policyVersion().equals(input.scorePolicyVersion())) {
                throw new IllegalArgumentException("candidate score policy mismatch");
            }
            if (!candidate.componentPolicyVersions().equals(input.componentPolicyVersions())) {
                throw new IllegalArgumentException("candidate component policy vector mismatch");
            }
            String identity = candidate.entityType().wireValue() + '\u0000' + candidate.entityId();
            if (!seen.add(identity)) {
                throw new IllegalArgumentException(
                        "duplicate candidate identity: " + candidate.entityType().wireValue() + "/" + candidate.entityId()
                );
            }
        }

        List<ValidScoredCandidate> scored = new ArrayList<>();
        List<CandidateScoreResult> terminal = new ArrayList<>();
        for (CandidateScoreResult candidate : candidates) {
            if (candidate.status() == CandidateScoreStatus.SCORED) {
                double rawScore = candidate.score().doubleValue();
                scored.add(new ValidScoredCandidate(
                        candidate,
                        rawScore == 0.0d ? 0.0d : rawScore,
                        RankingContracts.entityRank(policy, candidate.entityType())
                ));
            } else {
                terminal.add(candidate);
            }
        }

        Comparator<ValidScoredCandidate> scoredComparator = (left, right) -> {
            int scoreOrder = Double.compare(right.comparisonScore(), left.comparisonScore());
            if (scoreOrder != 0) {
                return scoreOrder;
            }
            int neutralOrder = Double.compare(
                    left.source().neutralFilledWeight(), right.source().neutralFilledWeight()
            );
            if (neutralOrder != 0) {
                return neutralOrder;
            }
            int typeOrder = Integer.compare(left.entityTypeRank(), right.entityTypeRank());
            if (typeOrder != 0) {
                return typeOrder;
            }
            return Utf16CodeUnitComparator.ASCENDING.compare(
                    left.source().entityId(), right.source().entityId()
            );
        };
        scored.sort(scoredComparator);
        terminal.sort((left, right) -> {
            int typeOrder = Integer.compare(
                    RankingContracts.entityRank(policy, left.entityType()),
                    RankingContracts.entityRank(policy, right.entityType())
            );
            return typeOrder != 0 ? typeOrder
                    : Utf16CodeUnitComparator.ASCENDING.compare(left.entityId(), right.entityId());
        });

        List<RankedCandidate> rankedCandidates = new ArrayList<>();
        for (int index = 0; index < scored.size(); index++) {
            ValidScoredCandidate candidate = scored.get(index);
            CandidateScoreResult source = candidate.source();
            rankedCandidates.add(new RankedCandidate(
                    index + 1,
                    source.entityId(),
                    source.entityType(),
                    source.score(),
                    source.scoredWeight(),
                    source.neutralFilledWeight(),
                    source.compositionMode(),
                    source.policyVersion(),
                    new RankingSortKey(
                            candidate.comparisonScore(),
                            source.neutralFilledWeight(),
                            candidate.entityTypeRank(),
                            source.entityId()
                    )
            ));
        }
        List<TerminalCandidateAudit> terminalCandidates = terminal.stream()
                .map(candidate -> new TerminalCandidateAudit(
                        candidate.entityId(), candidate.entityType(), candidate.status(),
                        candidate.notApplicableReason(), candidate.hardExclusionReason(), candidate.policyVersion()
                ))
                .toList();

        return new BaseRankingSnapshot(
                copyVersions(input.componentPolicyVersions()),
                candidates.size(),
                rankedCandidates.size(),
                terminalCandidates.size(),
                rankedCandidates,
                terminalCandidates
        );
    }

    static ScoreComponentPolicyVersions copyVersions(ScoreComponentPolicyVersions versions) {
        return new ScoreComponentPolicyVersions(
                versions.contextMatch(), versions.interestMatch(), versions.freshness(), versions.popularity()
        );
    }
}
