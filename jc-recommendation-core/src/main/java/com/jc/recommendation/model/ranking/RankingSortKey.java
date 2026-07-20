package com.jc.recommendation.model.ranking;

import java.util.Objects;

public record RankingSortKey(
        double score,
        double neutralFilledWeight,
        int entityTypeRank,
        String entityId
) {
    public RankingSortKey {
        Objects.requireNonNull(entityId, "entityId");
    }
}
