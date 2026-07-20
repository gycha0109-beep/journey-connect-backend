package com.jc.recommendation.policy;

import com.jc.recommendation.model.entity.RecommendationEntityType;

import java.time.Instant;
import java.util.List;

public final class RankingPolicies {
    public static final RankingPolicy V1 = new RankingPolicy(
            "ranking-v1",
            Instant.parse("2026-07-01T00:00:00Z"),
            "score-composition-v1",
            CandidateLimitPolicies.MAX_CANDIDATES_TO_SCORE,
            CandidateLimitPolicies.DEFAULT_RESULT_LIMIT,
            CandidateLimitPolicies.HARD_RESULT_LIMIT,
            List.of(
                    RecommendationEntityType.POST,
                    RecommendationEntityType.JOURNEY,
                    RecommendationEntityType.PLACE,
                    RecommendationEntityType.CREW
            ),
            List.of(
                    RecommendationEntityType.POST,
                    RecommendationEntityType.JOURNEY,
                    RecommendationEntityType.PLACE,
                    RecommendationEntityType.CREW
            ),
            "scored",
            "descending",
            "exact_number_equality",
            "ascending",
            "utf16_code_unit_ascending",
            "ranking-cursor-v1",
            "disabled_after_base_ranking",
            "disabled_after_diversity",
            "after_future_reranking_stages",
            "after_cursor_boundary"
    );

    private RankingPolicies() {
    }
}
