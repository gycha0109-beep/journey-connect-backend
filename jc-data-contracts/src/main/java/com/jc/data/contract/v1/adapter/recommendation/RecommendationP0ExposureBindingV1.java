package com.jc.data.contract.v1.adapter.recommendation;

import java.util.Objects;

public record RecommendationP0ExposureBindingV1(
        String authorityId,
        String exposureEventRef,
        String runId,
        String entityKey,
        int absoluteRank,
        int pagePosition,
        String surface,
        String bindingVersion) {
    public RecommendationP0ExposureBindingV1 {
        Objects.requireNonNull(authorityId, "authorityId");
        Objects.requireNonNull(exposureEventRef, "exposureEventRef");
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(entityKey, "entityKey");
        Objects.requireNonNull(surface, "surface");
        if (absoluteRank < 1 || pagePosition < 1) {
            throw new IllegalArgumentException("rank and page position must be positive");
        }
        if (!exposureEventRef.matches("exposure:[A-Za-z0-9][A-Za-z0-9._~-]{0,127}")) {
            throw new IllegalArgumentException("invalid exposure reference");
        }
        if (!"recommendation-general-exposure-binding-v1".equals(bindingVersion)) {
            throw new IllegalArgumentException("unsupported exposure binding version");
        }
    }
}
