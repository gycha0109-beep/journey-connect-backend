package com.jc.recommendation.integration;

import com.jc.recommendation.diversity.DiversityReranker;
import com.jc.recommendation.exploration.ExplorationCandidateInserter;
import com.jc.recommendation.model.diversity.DiversifiedCandidate;
import com.jc.recommendation.model.diversity.DiversityRerankInput;
import com.jc.recommendation.model.diversity.DiversityRerankResult;
import com.jc.recommendation.model.exploration.ExplorationFinalCandidate;
import com.jc.recommendation.model.exploration.InsertExplorationCandidatesInput;
import com.jc.recommendation.model.exploration.InsertExplorationCandidatesResult;
import com.jc.recommendation.model.integration.RankCandidatesWithExplorationInput;
import com.jc.recommendation.model.integration.RankCandidatesWithExplorationResult;
import com.jc.recommendation.model.ranking.BaseRankingSnapshot;
import com.jc.recommendation.model.ranking.BuildBaseRankingSnapshotInput;
import com.jc.recommendation.model.ranking.RankingEmptyReason;
import com.jc.recommendation.model.ranking.RankingResultStatus;
import com.jc.recommendation.model.score.CandidateScoreResult;
import com.jc.recommendation.model.score.CandidateScoreStatus;
import com.jc.recommendation.policy.DiversityPolicies;
import com.jc.recommendation.policy.DiversityPolicy;
import com.jc.recommendation.policy.ExplorationEnabledRankingPolicy;
import com.jc.recommendation.policy.ExplorationPolicies;
import com.jc.recommendation.policy.ExplorationPolicy;
import com.jc.recommendation.policy.RankingIntegrationPolicies;
import com.jc.recommendation.policy.RankingPolicies;
import com.jc.recommendation.ranking.BaseRankingSnapshotBuilder;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ExplorationEnabledRanker {
    private final BaseRankingSnapshotBuilder snapshotBuilder = new BaseRankingSnapshotBuilder();
    private final DiversityReranker diversityReranker = new DiversityReranker();
    private final ExplorationCandidateInserter explorationInserter = new ExplorationCandidateInserter();

    public RankCandidatesWithExplorationResult rank(RankCandidatesWithExplorationInput input) {
        RankingIntegrationContracts.nonBlank(input.rankingSnapshotId(), "rankingSnapshotId");
        RankingIntegrationContracts.nonBlank(input.metadataSnapshotId(), "metadataSnapshotId");
        RankingIntegrationContracts.nonBlank(input.explorationSnapshotId(), "explorationSnapshotId");
        RankingIntegrationContracts.nonBlank(input.userId(), "userId");
        RankingIntegrationContracts.nonBlank(input.contextId(), "contextId");
        RankingIntegrationContracts.nonBlank(input.scorePolicyVersion(), "scorePolicyVersion");
        RankingIntegrationContracts.seed(input.explorationSeed(), "explorationSeed");
        ExplorationEnabledRankingPolicy policy = input.policy() == null ? RankingIntegrationPolicies.V3 : input.policy();
        RankingIntegrationContracts.validateV3(policy);
        DiversityPolicy diversityPolicy = input.diversityPolicy() == null ? DiversityPolicies.V1 : input.diversityPolicy();
        ExplorationPolicy explorationPolicy = input.explorationPolicy() == null ? ExplorationPolicies.V1 : input.explorationPolicy();
        validateCompatibility(input, policy, diversityPolicy, explorationPolicy);
        Integer requestedLimit = input.resultLimit();
        if (requestedLimit != null) RankingIntegrationContracts.positive(requestedLimit.intValue(), "resultLimit");
        int effectiveLimit = requestedLimit == null ? policy.defaultResultLimit() : requestedLimit.intValue();
        if (effectiveLimit > policy.hardResultLimit()) throw new IllegalArgumentException("resultLimit exceeds hardResultLimit");
        if (input.candidates().size() > policy.maxInputCandidates()) throw new IllegalArgumentException("candidate count exceeds maxInputCandidates");

        BaseRankingSnapshot base = snapshotBuilder.build(new BuildBaseRankingSnapshotInput(
                input.userId(), input.contextId(), input.scorePolicyVersion(),
                input.componentPolicyVersions(), input.candidates()
        ), RankingPolicies.V1);
        List<CandidateScoreResult> rawTerminal = input.candidates().stream()
                .filter(candidate -> candidate.status() != CandidateScoreStatus.SCORED)
                .sorted(RankingIntegrationMapper.terminalComparator())
                .toList();
        DiversityRerankResult diversityResult = diversityReranker.rerank(new DiversityRerankInput(
                input.rankingSnapshotId(), input.metadataSnapshotId(), policy.baseRankingPolicyVersion(),
                input.scorePolicyVersion(), base.rankedCandidates(), input.candidateMetadata(), diversityPolicy
        ));
        List<DiversifiedCandidate> diversifiedCandidates = diversityResult.diversifiedCandidates();
        InsertExplorationCandidatesResult explorationResult = explorationInserter.insert(new InsertExplorationCandidatesInput(
                input.rankingSnapshotId(), input.metadataSnapshotId(), input.explorationSnapshotId(),
                policy.baseIntegrationPolicyVersion(), input.scorePolicyVersion(), diversityPolicy.policyVersion(),
                input.explorationSeed(), diversifiedCandidates, rawTerminal, input.explorationMetadata(),
                explorationPolicy, diversityPolicy
        ));
        List<ExplorationFinalCandidate> allFinal = explorationResult.finalCandidates();
        List<CandidateScoreResult> remainingTerminal = explorationResult.remainingTerminalCandidates();
        assertFinalPartition(input.candidates(), allFinal, remainingTerminal, base.scoredCandidateCount());
        if (base.inputCount() != allFinal.size() + remainingTerminal.size()) throw new IllegalStateException("input partition count invariant failed");
        if (allFinal.size() != base.scoredCandidateCount() + explorationResult.insertedCandidateCount()) throw new IllegalStateException("final ranked count invariant failed");
        if (remainingTerminal.size() != base.terminalCandidateCount() - explorationResult.insertedCandidateCount()) throw new IllegalStateException("remaining terminal count invariant failed");

        int startIndex = 0;
        if (input.cursor() != null) {
            RankingCursorV3Codec.Payload cursor = RankingCursorV3Codec.decode(input.cursor());
            if (!cursor.rankingSnapshotId().equals(input.rankingSnapshotId())) throw new IllegalArgumentException("cursor ranking snapshot mismatch");
            if (!cursor.metadataSnapshotId().equals(input.metadataSnapshotId())) throw new IllegalArgumentException("cursor metadata snapshot mismatch");
            if (!cursor.explorationSnapshotId().equals(input.explorationSnapshotId())) throw new IllegalArgumentException("cursor exploration snapshot mismatch");
            if (!cursor.rankingPolicyVersion().equals(policy.policyVersion())) throw new IllegalArgumentException("cursor Ranking policy mismatch");
            if (!cursor.scorePolicyVersion().equals(input.scorePolicyVersion())) throw new IllegalArgumentException("cursor Score policy mismatch");
            if (!cursor.componentPolicyVersions().equals(input.componentPolicyVersions())) throw new IllegalArgumentException("cursor component policy vector mismatch");
            if (!cursor.diversityPolicyVersion().equals(diversityPolicy.policyVersion())) throw new IllegalArgumentException("cursor Diversity policy mismatch");
            if (!cursor.explorationPolicyVersion().equals(explorationPolicy.policyVersion())) throw new IllegalArgumentException("cursor Exploration policy mismatch");
            if (!cursor.explorationSeed().equals(input.explorationSeed())) throw new IllegalArgumentException("cursor Exploration seed mismatch");
            if (cursor.lastAbsoluteRank() > allFinal.size()) throw new IllegalArgumentException("cursor rank is outside final candidates");
            ExplorationFinalCandidate boundary = allFinal.get(cursor.lastAbsoluteRank() - 1);
            if (boundary.entityType() != cursor.lastEntityType() || !boundary.entityId().equals(cursor.lastEntityId())) throw new IllegalArgumentException("cursor final rank identity mismatch");
            startIndex = cursor.lastAbsoluteRank();
        }

        int endExclusive = Math.min(startIndex + effectiveLimit, allFinal.size());
        List<ExplorationFinalCandidate> page = List.copyOf(allFinal.subList(startIndex, endExclusive));
        boolean hasNextPage = startIndex + page.size() < allFinal.size();
        ExplorationFinalCandidate lastItem = page.isEmpty() ? null : page.getLast();
        String nextCursor = hasNextPage && lastItem != null ? RankingCursorV3Codec.encode(new RankingCursorV3Codec.Payload(
                input.rankingSnapshotId(), input.metadataSnapshotId(), input.explorationSnapshotId(),
                policy.policyVersion(), input.scorePolicyVersion(), input.componentPolicyVersions(),
                diversityPolicy.policyVersion(), explorationPolicy.policyVersion(), input.explorationSeed(),
                lastItem.absoluteRank(), lastItem.entityType(), lastItem.entityId()
        )) : null;
        boolean empty = allFinal.isEmpty();
        return new RankCandidatesWithExplorationResult(
                input.rankingSnapshotId(), input.metadataSnapshotId(), input.explorationSnapshotId(),
                input.userId(), input.contextId(),
                empty ? RankingResultStatus.EMPTY : RankingResultStatus.RANKED,
                empty ? RankingEmptyReason.NO_SCORED_CANDIDATES : null,
                policy.policyVersion(), policy.baseIntegrationPolicyVersion(), policy.baseRankingPolicyVersion(),
                input.scorePolicyVersion(), base.componentPolicyVersions(), diversityPolicy.policyVersion(),
                explorationPolicy.policyVersion(), input.explorationSeed(), base.inputCount(),
                base.scoredCandidateCount(), base.terminalCandidateCount(), diversifiedCandidates.size(),
                explorationResult.summary().structurallyEligibleCandidateCount(), explorationResult.eligibleCandidateCount(),
                explorationResult.insertedCandidateCount(), allFinal.size(), remainingTerminal.size(), requestedLimit,
                effectiveLimit, page.isEmpty() ? null : page.getFirst().absoluteRank(),
                lastItem == null ? null : lastItem.absoluteRank(), hasNextPage, nextCursor,
                RankingIntegrationMapper.mapSummary(diversityResult), explorationResult.summary(), page,
                RankingIntegrationMapper.terminalAudits(remainingTerminal)
        );
    }

    private static void validateCompatibility(
            RankCandidatesWithExplorationInput input,
            ExplorationEnabledRankingPolicy policy,
            DiversityPolicy diversityPolicy,
            ExplorationPolicy explorationPolicy
    ) {
        if (!policy.baseIntegrationPolicyVersion().equals(RankingIntegrationPolicies.V2.policyVersion())) throw new IllegalArgumentException("base Integration policy mismatch");
        if (!policy.baseRankingPolicyVersion().equals(RankingPolicies.V1.policyVersion())) throw new IllegalArgumentException("base Ranking policy mismatch");
        if (!policy.expectedScorePolicyVersion().equals(input.scorePolicyVersion())) throw new IllegalArgumentException("scorePolicyVersion does not match Ranking v3 policy");
        if (!policy.expectedDiversityPolicyVersion().equals(diversityPolicy.policyVersion())) throw new IllegalArgumentException("Diversity policy does not match Ranking v3 policy");
        if (!policy.expectedExplorationPolicyVersion().equals(explorationPolicy.policyVersion())) throw new IllegalArgumentException("Exploration policy does not match Ranking v3 policy");
        if (!explorationPolicy.expectedRankingPolicyVersion().equals(policy.baseIntegrationPolicyVersion())) throw new IllegalArgumentException("Exploration policy Ranking version mismatch");
        if (!explorationPolicy.expectedScorePolicyVersion().equals(input.scorePolicyVersion())) throw new IllegalArgumentException("Exploration policy Score version mismatch");
        if (!explorationPolicy.expectedDiversityPolicyVersion().equals(diversityPolicy.policyVersion())) throw new IllegalArgumentException("Exploration policy Diversity version mismatch");
    }

    private static void assertFinalPartition(
            List<CandidateScoreResult> sourceCandidates,
            List<ExplorationFinalCandidate> finalCandidates,
            List<CandidateScoreResult> remainingTerminal,
            int scoredCount
    ) {
        Set<String> source = new HashSet<>();
        for (CandidateScoreResult candidate : sourceCandidates) source.add(identity(candidate.entityType().wireValue(), candidate.entityId()));
        Set<String> seen = new HashSet<>();
        int personalizedCount = 0;
        for (int index = 0; index < finalCandidates.size(); index++) {
            ExplorationFinalCandidate candidate = finalCandidates.get(index);
            if (candidate.absoluteRank() != index + 1) throw new IllegalStateException("final absolute rank invariant failed");
            String key = identity(candidate.entityType().wireValue(), candidate.entityId());
            if (!source.contains(key)) throw new IllegalStateException("synthetic final identity");
            if (!seen.add(key)) throw new IllegalStateException("duplicate final identity");
            if (candidate.origin().wireValue().equals("personalized")) personalizedCount++;
        }
        if (personalizedCount != scoredCount) throw new IllegalStateException("personalized count invariant failed");
        for (CandidateScoreResult candidate : remainingTerminal) {
            String key = identity(candidate.entityType().wireValue(), candidate.entityId());
            if (!source.contains(key) || !seen.add(key)) throw new IllegalStateException("identity partition invariant failed");
        }
        if (seen.size() != source.size()) throw new IllegalStateException("full identity partition invariant failed");
    }

    private static String identity(String entityType, String entityId) {
        return entityType + '\u0000' + entityId;
    }
}
