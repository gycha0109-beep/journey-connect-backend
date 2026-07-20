package com.jc.recommendation.model.feature;

import java.time.Instant;
import java.util.Objects;

public record ExplicitPreference(
        String userId,
        String featureId,
        PreferenceKind preference,
        double strength,
        Instant updatedAt
) {
    public ExplicitPreference {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(featureId, "featureId");
        Objects.requireNonNull(preference, "preference");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
