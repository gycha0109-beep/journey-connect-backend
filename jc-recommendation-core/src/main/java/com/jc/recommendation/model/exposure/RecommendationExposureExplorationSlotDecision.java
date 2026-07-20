package com.jc.recommendation.model.exposure;

import com.jc.recommendation.model.exploration.ExplorationSlotDecisionStatus;
import java.util.Objects;

public record RecommendationExposureExplorationSlotDecision(int targetInsertionRank, ExplorationSlotDecisionStatus status) {
    public RecommendationExposureExplorationSlotDecision { Objects.requireNonNull(status, "status"); }
}
