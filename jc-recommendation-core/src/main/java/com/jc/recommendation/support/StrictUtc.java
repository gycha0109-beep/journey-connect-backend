package com.jc.recommendation.support;

import java.time.DateTimeException;
import java.time.Instant;
import java.util.Objects;

public final class StrictUtc {
    private StrictUtc() {
    }

    public static Instant parse(String value, String fieldName) {
        Objects.requireNonNull(fieldName, "fieldName");
        if (value == null || !value.endsWith("Z")) {
            throw new IllegalArgumentException(fieldName + " must be an ISO 8601 UTC string ending in Z");
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException(fieldName + " must be a valid ISO 8601 UTC string", exception);
        }
    }

    public static long parseEpochMilli(String value, String fieldName) {
        return parse(value, fieldName).toEpochMilli();
    }
}
