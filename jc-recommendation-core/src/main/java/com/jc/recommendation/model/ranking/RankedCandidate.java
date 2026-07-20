package com.jc.recommendation.model.ranking;

import com.jc.recommendation.model.entity.RecommendationEntityType;
import com.jc.recommendation.model.score.ScoreCompositionMode;

import java.util.Objects;

public record RankedCandidate(
        int absoluteRank,
        String entityId,
        RecommendationEntityType entityType,
        double score,
        double scoredWeight,
        double neutralFilledWeight,
        ScoreCompositionMode compositionMode,
        String scorePolicyVersion,
        RankingSortKey sortKey
) {
    public RankedCandidate {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(compositionMode, "compositionMode");
        Objects.requireNonNull(scorePolicyVersion, "scorePolicyVersion");
        Objects.requireNonNull(sortKey, "sortKey");
    }
}
