package com.jc.recommendation.model.exploration;

import java.util.Objects;

public record ExplorationQualityEvidence(
        ExplorationQualityComponent component,
        double rawScore,
        int configuredWeight,
        double weightedContribution
) {
    public ExplorationQualityEvidence {
        Objects.requireNonNull(component, "component");
    }
}
