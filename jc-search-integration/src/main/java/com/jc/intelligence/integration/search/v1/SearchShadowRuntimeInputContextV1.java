package com.jc.intelligence.integration.search.v1;

import java.time.Instant;
import java.util.Objects;

public record SearchShadowRuntimeInputContextV1(
        String correlationId,
        Instant referenceTime,
        String legacyRequestFingerprint,
        String legacyResponseFingerprint) {
    public SearchShadowRuntimeInputContextV1 {
        if (correlationId == null || correlationId.isBlank()) throw new IllegalArgumentException("correlationId is required");
        Objects.requireNonNull(referenceTime, "referenceTime");
        legacyRequestFingerprint = requireHash(legacyRequestFingerprint, "legacyRequestFingerprint");
        legacyResponseFingerprint = requireHash(legacyResponseFingerprint, "legacyResponseFingerprint");
    }

    private static String requireHash(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be lowercase SHA-256");
        }
        return value;
    }
}
