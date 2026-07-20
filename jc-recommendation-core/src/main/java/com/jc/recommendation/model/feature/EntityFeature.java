package com.jc.recommendation.model.feature;

import java.time.Instant;
import java.util.Objects;

public record EntityFeature(
        String entityId,
        String featureId,
        double weight,
        Double confidence,
        FeatureSource source,
        FeatureValidationStatus validationStatus,
        Instant updatedAt
) {
    public EntityFeature {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(featureId, "featureId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(validationStatus, "validationStatus");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
