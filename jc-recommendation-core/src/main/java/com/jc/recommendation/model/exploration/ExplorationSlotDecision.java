package com.jc.recommendation.model.exploration;

import com.jc.recommendation.model.entity.RecommendationEntityType;

import java.util.Objects;

public record ExplorationSlotDecision(
        int targetInsertionRank,
        ExplorationSlotDecisionStatus status,
        RecommendationEntityType selectedEntityType,
        String selectedEntityId
) {
    public ExplorationSlotDecision {
        Objects.requireNonNull(status, "status");
    }
}
