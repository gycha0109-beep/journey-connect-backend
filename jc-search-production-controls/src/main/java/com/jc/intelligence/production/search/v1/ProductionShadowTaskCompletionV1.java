package com.jc.intelligence.production.search.v1;

import java.time.Duration;
import java.util.Objects;

public record ProductionShadowTaskCompletionV1(
        ProductionShadowTaskCompletionStatus status,
        Duration elapsed,
        String safeReason) {
    public ProductionShadowTaskCompletionV1 {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(elapsed, "elapsed");
        if (elapsed.isNegative()) {
            throw new IllegalArgumentException("elapsed must not be negative");
        }
        if (safeReason == null || !safeReason.matches("[a-z][a-z0-9_]{0,63}")) {
            throw new IllegalArgumentException("safeReason must be bounded lowercase_snake_case");
        }
    }
}
