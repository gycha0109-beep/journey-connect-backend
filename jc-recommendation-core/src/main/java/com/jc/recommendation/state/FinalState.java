package com.jc.recommendation.state;

import java.util.Objects;

public record FinalState(
        String key,
        String userId,
        String entityId,
        String family,
        boolean active
) {
    public FinalState {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(family, "family");
    }
}
