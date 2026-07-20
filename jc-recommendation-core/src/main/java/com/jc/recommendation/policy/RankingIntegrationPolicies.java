package com.jc.recommendation.policy;

import java.time.Instant;

public final class RankingIntegrationPolicies {
    public static final DiversityEnabledRankingPolicy V2 = new DiversityEnabledRankingPolicy(
            "ranking-v2", Instant.parse("2026-07-01T00:00:00Z"),
            "ranking-v1", RankingPolicies.V1.expectedScorePolicyVersion(), DiversityPolicies.V1.policyVersion(),
            CandidateLimitPolicies.MAX_CANDIDATES_TO_SCORE,
            CandidateLimitPolicies.DEFAULT_RESULT_LIMIT,
            CandidateLimitPolicies.HARD_RESULT_LIMIT,
            RankingPolicies.V1.eligibleEntityTypes(), RankingPolicies.V1.entityTypeOrder(),
            RankingPolicies.V1.rankedStatus(), RankingPolicies.V1.scoreDirection(), RankingPolicies.V1.scoreEquality(),
            RankingPolicies.V1.neutralFilledWeightDirection(), RankingPolicies.V1.entityIdComparison(),
            "ranking-cursor-v2", "enabled_after_base_ranking", "disabled_after_diversity",
            "after_diversity_and_exploration", "after_cursor_boundary", "exactly_scored_candidates",
            "diversified_absolute_rank"
    );

    public static final ExplorationEnabledRankingPolicy V3 = new ExplorationEnabledRankingPolicy(
            "ranking-v3", Instant.parse("2026-07-01T00:00:00Z"),
            "ranking-v1", "ranking-v2", V2.expectedScorePolicyVersion(),
            DiversityPolicies.V1.policyVersion(), ExplorationPolicies.V1.policyVersion(),
            CandidateLimitPolicies.MAX_CANDIDATES_TO_SCORE,
            CandidateLimitPolicies.DEFAULT_RESULT_LIMIT,
            CandidateLimitPolicies.HARD_RESULT_LIMIT,
            V2.eligibleEntityTypes(), V2.entityTypeOrder(), V2.rankedStatus(), V2.scoreDirection(), V2.scoreEquality(),
            V2.neutralFilledWeightDirection(), V2.entityIdComparison(),
            "ranking-cursor-v3", "enabled_after_base_ranking", "enabled_after_diversity",
            "after_exploration", "after_cursor_boundary", "exactly_scored_candidates",
            "exactly_structurally_eligible_candidates", "inserted_exploration_removed_from_terminal",
            "exploration_final_absolute_rank"
    );

    private RankingIntegrationPolicies() {
    }
}
