package com.jc.recommendation.policy;

import java.util.Objects;

public record TimeDecayBucket(
        String id,
        Double maxElapsedDays,
        double multiplier
) {
    public TimeDecayBucket {
        Objects.requireNonNull(id, "id");
    }
}
