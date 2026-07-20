package com.jc.intelligence.integration.search.v1;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SearchShadowContextV1(
        String requestId,
        String correlationId,
        String sessionRef,
        Instant referenceTime) {
    public SearchShadowContextV1 {
        if (requestId != null) requestId = requireTrackingId(requestId, "requestId");
        correlationId = requireTrackingId(correlationId, "correlationId");
        if (sessionRef != null) sessionRef = requireSafeRef(sessionRef, "sessionRef");
        Objects.requireNonNull(referenceTime, "referenceTime");
    }

    private static String requireTrackingId(String value, String field) {
        String checked = requireSafeRef(value, field);
        if (!checked.contains(":")) {
            try { UUID.fromString(checked); }
            catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(field + " must be a UUID or prefixed opaque ID");
            }
        }
        return checked;
    }

    private static String requireSafeRef(String value, String field) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > 160
                || value.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException(field + " must be trimmed nonblank text without whitespace");
        }
        return value;
    }
}
