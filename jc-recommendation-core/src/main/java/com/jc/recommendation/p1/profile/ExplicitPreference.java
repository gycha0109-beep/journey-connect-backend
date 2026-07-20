package com.jc.recommendation.p1.profile;

import com.jc.recommendation.model.feature.PreferenceKind;
import java.util.Objects;

public record ExplicitPreference(String featureId, PreferenceKind direction, double strength) {
    public ExplicitPreference {
        Objects.requireNonNull(featureId, "featureId");
        Objects.requireNonNull(direction, "direction");
        if (featureId.isBlank()) {
            throw new IllegalArgumentException("featureId must not be blank");
        }
        if (!Double.isFinite(strength) || strength < 0.0d || strength > 1.0d) {
            throw new IllegalArgumentException("strength must be finite and within [0,1]");
        }
    }
}
