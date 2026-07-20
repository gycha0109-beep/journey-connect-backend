package com.jc.intelligence.integration.search.v1;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record SearchShadowExecutionDeadlineV1(Instant referenceTime, Duration timeout) {
    public SearchShadowExecutionDeadlineV1 {
        Objects.requireNonNull(referenceTime, "referenceTime");
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) throw new IllegalArgumentException("timeout must be positive");
    }

    public Instant deadlineAt() { return referenceTime.plus(timeout); }
}
