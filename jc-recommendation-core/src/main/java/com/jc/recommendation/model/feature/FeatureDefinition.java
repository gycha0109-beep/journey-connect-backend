package com.jc.recommendation.model.feature;

import java.util.Objects;

public record FeatureDefinition(
        String id,
        FeatureGroup group,
        String key,
        String displayName,
        FeatureStatus status,
        String replacementFeatureId
) {
    public FeatureDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(group, "group");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(status, "status");
    }

    public static FeatureDefinition active(FeatureGroup group, String key, String displayName) {
        return new FeatureDefinition(
                group.wireValue() + ":" + key,
                group,
                key,
                displayName,
                FeatureStatus.ACTIVE,
                null
        );
    }
}
