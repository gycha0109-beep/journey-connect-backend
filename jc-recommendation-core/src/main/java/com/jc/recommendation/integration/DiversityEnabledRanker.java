package com.jc.recommendation.integration;

import com.jc.recommendation.diversity.DiversityReranker;
import com.jc.recommendation.model.diversity.DiversityRerankInput;
import com.jc.recommendation.model.diversity.DiversityRerankResult;
import com.jc.recommendation.model.integration.DiversityRankedCandidate;
import com.jc.recommendation.model.integration.RankCandidatesWithDiversityInput;
import com.jc.recommendation.model.integration.RankCandidatesWithDiversityResult;
import com.jc.recommendation.model.ranking.BaseRankingSnapshot;
import com.jc.recommendation.model.ranking.BuildBaseRankingSnapshotInput;
import com.jc.recommendation.model.ranking.RankingEmptyReason;
import com.jc.recommendation.model.ranking.RankingResultStatus;
import com.jc.recommendation.policy.DiversityEnabledRankingPolicy;
import com.jc.recommendation.policy.DiversityPolicies;
import com.jc.recommendation.policy.DiversityPolicy;
import com.jc.recommendation.policy.RankingIntegrationPolicies;
import com.jc.recommendation.policy.RankingPolicies;
import com.jc.recommendation.ranking.BaseRankingSnapshotBuilder;

import java.util.List;

public final class DiversityEnabledRanker {
    private final BaseRankingSnapshotBuilder snapshotBuilder = new BaseRankingSnapshotBuilder();
    private final DiversityReranker diversityReranker = new DiversityReranker();

    public RankCandidatesWithDiversityResult rank(RankCandidatesWithDiversityInput input) {
        RankingIntegrationContracts.nonBlank(input.rankingSnapshotId(), "rankingSnapshotId");
        RankingIntegrationContracts.nonBlank(input.metadataSnapshotId(), "metadataSnapshotId");
        RankingIntegrationContracts.nonBlank(input.userId(), "userId");
        RankingIntegrationContracts.nonBlank(input.contextId(), "contextId");
        RankingIntegrationContracts.nonBlank(input.scorePolicyVersion(), "scorePolicyVersion");
        DiversityEnabledRankingPolicy policy = input.policy() == null ? RankingIntegrationPolicies.V2 : input.policy();
        RankingIntegrationContracts.validateV2(policy);
        DiversityPolicy diversityPolicy = input.diversityPolicy() == null ? DiversityPolicies.V1 : input.diversityPolicy();
        if (!policy.baseRankingPolicyVersion().equals(diversityPolicy.expectedRankingPolicyVersion())) throw new IllegalArgumentException("integration base Ranking policy is incompatible with Diversity policy");
        if (!policy.expectedScorePolicyVersion().equals(diversityPolicy.expectedScorePolicyVersion())) throw new IllegalArgumentException("integration Score policy is incompatible with Diversity policy");
        if (!input.scorePolicyVersion().equals(policy.expectedScorePolicyVersion())) throw new IllegalArgumentException("scorePolicyVersion does not match integration policy expectation");
        if (!policy.expectedDiversityPolicyVersion().equals(diversityPolicy.policyVersion())) throw new IllegalArgumentException("Diversity policy version does not match integration policy expectation");
        Integer requestedLimit = input.resultLimit();
        if (requestedLimit != null) RankingIntegrationContracts.positive(requestedLimit.intValue(), "resultLimit");
        int effectiveLimit = requestedLimit == null ? policy.defaultResultLimit() : requestedLimit.intValue();
        if (effectiveLimit > policy.hardResultLimit()) throw new IllegalArgumentException("resultLimit exceeds hardResultLimit");
        if (input.candidates().size() > policy.maxInputCandidates()) throw new IllegalArgumentException("candidate count exceeds maxInputCandidates");

        BaseRankingSnapshot base = snapshotBuilder.build(new BuildBaseRankingSnapshotInput(
                input.userId(), input.contextId(), input.scorePolicyVersion(),
                input.componentPolicyVersions(), input.candidates()
        ), RankingPolicies.V1);
        DiversityRerankResult diversityResult = diversityReranker.rerank(new DiversityRerankInput(
                input.rankingSnapshotId(), input.metadataSnapshotId(), policy.baseRankingPolicyVersion(),
                input.scorePolicyVersion(), base.rankedCandidates(), input.candidateMetadata(), diversityPolicy
        ));
        List<DiversityRankedCandidate> allFinal = diversityResult.diversifiedCandidates().stream()
                .map(RankingIntegrationMapper::mapCandidate)
                .toList();
        assertFinalInvariants(base, allFinal);

        int startIndex = 0;
        if (input.cursor() != null) {
            RankingCursorV2Codec.Payload cursor = RankingCursorV2Codec.decode(input.cursor());
            if (!cursor.rankingSnapshotId().equals(input.rankingSnapshotId())) throw new IllegalArgumentException("cursor ranking snapshot mismatch");
            if (!cursor.metadataSnapshotId().equals(input.metadataSnapshotId())) throw new IllegalArgumentException("cursor metadata snapshot mismatch");
            if (!cursor.rankingPolicyVersion().equals(policy.policyVersion())) throw new IllegalArgumentException("cursor Ranking policy mismatch");
            if (!cursor.scorePolicyVersion().equals(input.scorePolicyVersion())) throw new IllegalArgumentException("cursor score policy mismatch");
            if (!cursor.componentPolicyVersions().equals(input.componentPolicyVersions())) throw new IllegalArgumentException("cursor component policy vector mismatch");
            if (!cursor.diversityPolicyVersion().equals(diversityPolicy.policyVersion())) throw new IllegalArgumentException("cursor Diversity policy mismatch");
            if (cursor.lastAbsoluteRank() > allFinal.size()) throw new IllegalArgumentException("cursor rank is outside diversified candidates");
            DiversityRankedCandidate boundary = allFinal.get(cursor.lastAbsoluteRank() - 1);
            if (boundary.entityType() != cursor.lastEntityType() || !boundary.entityId().equals(cursor.lastEntityId())) throw new IllegalArgumentException("cursor final rank identity mismatch");
            startIndex = cursor.lastAbsoluteRank();
        }

        int endExclusive = Math.min(startIndex + effectiveLimit, allFinal.size());
        List<DiversityRankedCandidate> page = List.copyOf(allFinal.subList(startIndex, endExclusive));
        boolean hasNextPage = startIndex + page.size() < allFinal.size();
        DiversityRankedCandidate lastItem = page.isEmpty() ? null : page.getLast();
        String nextCursor = hasNextPage && lastItem != null ? RankingCursorV2Codec.encode(new RankingCursorV2Codec.Payload(
                input.rankingSnapshotId(), input.metadataSnapshotId(), policy.policyVersion(), input.scorePolicyVersion(),
                input.componentPolicyVersions(), diversityPolicy.policyVersion(), lastItem.absoluteRank(),
                lastItem.entityType(), lastItem.entityId()
        )) : null;
        boolean empty = allFinal.isEmpty();
        return new RankCandidatesWithDiversityResult(
                input.rankingSnapshotId(), input.metadataSnapshotId(), input.userId(), input.contextId(),
                empty ? RankingResultStatus.EMPTY : RankingResultStatus.RANKED,
                empty ? RankingEmptyReason.NO_SCORED_CANDIDATES : null,
                policy.policyVersion(), policy.baseRankingPolicyVersion(), input.scorePolicyVersion(),
                base.componentPolicyVersions(), diversityPolicy.policyVersion(), base.inputCount(),
                base.scoredCandidateCount(), base.terminalCandidateCount(), requestedLimit, effectiveLimit,
                page.isEmpty() ? null : page.getFirst().absoluteRank(), lastItem == null ? null : lastItem.absoluteRank(),
                hasNextPage, nextCursor, RankingIntegrationMapper.mapSummary(diversityResult), page,
                base.terminalCandidates()
        );
    }

    private static void assertFinalInvariants(BaseRankingSnapshot base, List<DiversityRankedCandidate> finalCandidates) {
        if (base.rankedCandidates().size() != finalCandidates.size()) throw new IllegalStateException("Integration candidate count invariant failed");
        for (int index = 0; index < finalCandidates.size(); index++) {
            DiversityRankedCandidate candidate = finalCandidates.get(index);
            if (candidate.absoluteRank() != index + 1) throw new IllegalStateException("Integration final rank invariant failed");
        }
    }
}
