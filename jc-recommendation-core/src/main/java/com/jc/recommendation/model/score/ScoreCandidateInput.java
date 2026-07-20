package com.jc.recommendation.model.score;

import com.jc.recommendation.model.context.ContextEligibilityResult;
import com.jc.recommendation.model.context.ContextMatchResult;
import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.model.freshness.FreshnessResult;
import com.jc.recommendation.model.interest.InterestMatchResult;
import com.jc.recommendation.model.popularity.PopularityResult;
import com.jc.recommendation.policy.ScoreCompositionPolicy;

import java.util.Objects;

public record ScoreCandidateInput(
        String userId,
        String contextId,
        String entityId,
        RecommendationEntityType entityType,
        ContextEligibilityResult contextEligibilityResult,
        InterestMatchResult interestMatchResult,
        ContextMatchResult contextMatchResult,
        FreshnessResult freshnessResult,
        PopularityResult popularityResult,
        ScoreCompositionPolicy policy
) {
    public ScoreCandidateInput {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(contextId, "contextId");
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(contextEligibilityResult, "contextEligibilityResult");
        Objects.requireNonNull(interestMatchResult, "interestMatchResult");
        Objects.requireNonNull(contextMatchResult, "contextMatchResult");
        Objects.requireNonNull(freshnessResult, "freshnessResult");
        Objects.requireNonNull(popularityResult, "popularityResult");
    }
}
