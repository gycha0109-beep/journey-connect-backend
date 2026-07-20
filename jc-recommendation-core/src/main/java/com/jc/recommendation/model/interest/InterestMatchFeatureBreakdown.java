package com.jc.recommendation.model.interest;

import com.jc.recommendation.model.feature.FeatureSource;
import com.jc.recommendation.model.feature.PreferenceKind;

import java.util.Objects;

public record InterestMatchFeatureBreakdown(
        String featureId,
        double entityWeight,
        FeatureSource entitySource,
        PreferenceKind signalDirection,
        Double signalStrength,
        FeatureSource signalSource,
        double positiveContribution,
        double negativeContribution,
        boolean matched
) {
    public InterestMatchFeatureBreakdown {
        Objects.requireNonNull(featureId, "featureId");
        Objects.requireNonNull(entitySource, "entitySource");
    }
}
