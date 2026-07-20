package com.jc.recommendation.model.interest;

import com.jc.recommendation.model.feature.FeatureSource;
import com.jc.recommendation.model.feature.FeatureValidationStatus;
import com.jc.recommendation.model.feature.PreferenceKind;

import java.time.Instant;
import java.util.Objects;

public record UserInterestSignal(
        String signalId,
        String userId,
        String featureId,
        PreferenceKind direction,
        double strength,
        FeatureSource source,
        FeatureValidationStatus validationStatus,
        Instant updatedAt
) {
    public UserInterestSignal {
        Objects.requireNonNull(signalId, "signalId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(featureId, "featureId");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(validationStatus, "validationStatus");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
