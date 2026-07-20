package com.jc.intelligence.runtime.search.v1;

import java.time.Instant;
import java.util.Objects;

public record SearchRuntimeFailureV1(
        SearchRuntimeFailureCode failureCode,
        String safeMessage,
        String evidenceRef,
        boolean retryable,
        Instant occurredAt) {
    public SearchRuntimeFailureV1 {
        Objects.requireNonNull(failureCode, "failureCode");
        safeMessage = requireSafeText(safeMessage, "safeMessage");
        if (evidenceRef != null) evidenceRef = requireSafeText(evidenceRef, "evidenceRef");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (safeMessage.contains("Exception") || safeMessage.contains(" at ")) {
            throw new IllegalArgumentException("safeMessage must not contain stack-trace material");
        }
    }

    private static String requireSafeText(String value, String field) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > 256) {
            throw new IllegalArgumentException(field + " must be trimmed nonblank text up to 256 characters");
        }
        return value;
    }
}
