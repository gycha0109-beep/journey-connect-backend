package com.jc.recommendation.model.exploration;

import com.jc.recommendation.model.entity.RecommendationEntityType;

public sealed interface ExplorationFinalCandidate
        permits PersonalizedExplorationCandidate, InsertedExplorationCandidate {
    ExplorationCandidateOrigin origin();
    int absoluteRank();
    String entityId();
    RecommendationEntityType entityType();
}
