package com.jc.recommendation.ranking;

import com.jc.recommendation.model.ranking.BaseRankingSnapshot;
import com.jc.recommendation.model.ranking.BuildBaseRankingSnapshotInput;
import com.jc.recommendation.model.ranking.RankCandidatesInput;
import com.jc.recommendation.model.ranking.RankCandidatesResult;
import com.jc.recommendation.model.ranking.RankedCandidate;
import com.jc.recommendation.model.ranking.RankingEmptyReason;
import com.jc.recommendation.model.ranking.RankingResultStatus;
import com.jc.recommendation.policy.RankingPolicies;
import com.jc.recommendation.policy.RankingPolicy;

import java.util.List;

public final class CandidateRanker {
    private final BaseRankingSnapshotBuilder snapshotBuilder = new BaseRankingSnapshotBuilder();

    public RankCandidatesResult rank(RankCandidatesInput input) {
        RankingContracts.nonBlank(input.rankingSnapshotId(), "rankingSnapshotId");
        RankingPolicy policy = input.policy() == null ? RankingPolicies.V1 : input.policy();
        RankingContracts.validatePolicy(policy);
        Integer requestedLimit = input.resultLimit();
        if (requestedLimit != null) {
            RankingContracts.positive(requestedLimit.intValue(), "resultLimit");
        }
        int effectiveLimit = requestedLimit == null ? policy.defaultResultLimit() : requestedLimit.intValue();
        if (effectiveLimit > policy.hardResultLimit()) {
            throw new IllegalArgumentException("resultLimit exceeds hardResultLimit");
        }

        BaseRankingSnapshot snapshot = snapshotBuilder.build(new BuildBaseRankingSnapshotInput(
                input.userId(), input.contextId(), input.scorePolicyVersion(),
                input.componentPolicyVersions(), input.candidates()
        ), policy);
        List<RankedCandidate> allRanked = snapshot.rankedCandidates();

        int startIndex = 0;
        if (input.cursor() != null) {
            RankingCursorCodec.CursorPayload cursor = RankingCursorCodec.decode(input.cursor());
            if (!cursor.rankingSnapshotId().equals(input.rankingSnapshotId())) {
                throw new IllegalArgumentException("cursor snapshot mismatch");
            }
            if (!cursor.rankingPolicyVersion().equals(policy.policyVersion())) {
                throw new IllegalArgumentException("cursor Ranking policy mismatch");
            }
            if (!cursor.scorePolicyVersion().equals(input.scorePolicyVersion())) {
                throw new IllegalArgumentException("cursor score policy mismatch");
            }
            if (!cursor.componentPolicyVersions().equals(input.componentPolicyVersions())) {
                throw new IllegalArgumentException("cursor component policy vector mismatch");
            }
            if (cursor.lastAbsoluteRank() > allRanked.size()) {
                throw new IllegalArgumentException("cursor rank is outside ranked candidates");
            }
            RankedCandidate boundary = allRanked.get(cursor.lastAbsoluteRank() - 1);
            if (boundary.entityType() != cursor.lastEntityType()
                    || !boundary.entityId().equals(cursor.lastEntityId())) {
                throw new IllegalArgumentException("cursor rank identity mismatch");
            }
            startIndex = cursor.lastAbsoluteRank();
        }

        int pageEndExclusive = Math.min(startIndex + effectiveLimit, allRanked.size());
        List<RankedCandidate> page = List.copyOf(allRanked.subList(startIndex, pageEndExclusive));
        boolean hasNextPage = startIndex + page.size() < allRanked.size();
        RankedCandidate lastItem = page.isEmpty() ? null : page.getLast();
        String nextCursor = hasNextPage && lastItem != null
                ? RankingCursorCodec.encode(new RankingCursorCodec.CursorPayload(
                        input.rankingSnapshotId(), policy.policyVersion(), input.scorePolicyVersion(),
                        input.componentPolicyVersions(), lastItem.absoluteRank(),
                        lastItem.entityType(), lastItem.entityId()
                ))
                : null;

        boolean empty = allRanked.isEmpty();
        return new RankCandidatesResult(
                input.rankingSnapshotId(),
                input.userId(),
                input.contextId(),
                empty ? RankingResultStatus.EMPTY : RankingResultStatus.RANKED,
                empty ? RankingEmptyReason.NO_SCORED_CANDIDATES : null,
                policy.policyVersion(),
                input.scorePolicyVersion(),
                snapshot.componentPolicyVersions(),
                snapshot.inputCount(),
                snapshot.scoredCandidateCount(),
                snapshot.terminalCandidateCount(),
                requestedLimit,
                effectiveLimit,
                page.isEmpty() ? null : page.getFirst().absoluteRank(),
                lastItem == null ? null : lastItem.absoluteRank(),
                hasNextPage,
                nextCursor,
                page,
                snapshot.terminalCandidates()
        );
    }
}
