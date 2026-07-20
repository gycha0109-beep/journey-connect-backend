package com.jc.intelligence.integration.search.v1.fixture;

import java.util.List;
import java.util.Set;

public record SearchShadowFixtureCaseV1(
        String scenario,
        String mode,
        String runtimeInputStatus,
        String runtimeStatus,
        int topK,
        boolean timeout,
        List<String> legacyEntityRefs,
        List<String> runtimeEntityRefs) {
    public SearchShadowFixtureCaseV1 {
        if (scenario == null || scenario.isBlank() || !scenario.equals(scenario.trim())) {
            throw new IllegalArgumentException("scenario is required");
        }
        if (!Set.of("disabled", "test_only", "shadow_enabled", "unknown").contains(mode)) {
            throw new IllegalArgumentException("unsupported mode");
        }
        if (!Set.of("available", "unavailable", "unsupported", "invalid").contains(runtimeInputStatus)) {
            throw new IllegalArgumentException("unsupported runtimeInputStatus");
        }
        if (!Set.of("success", "no_results", "fallback", "failed").contains(runtimeStatus)) {
            throw new IllegalArgumentException("unsupported runtimeStatus");
        }
        if (topK < 1 || topK > 100) throw new IllegalArgumentException("topK must be 1..100");
        legacyEntityRefs = List.copyOf(legacyEntityRefs == null ? List.of() : legacyEntityRefs);
        runtimeEntityRefs = List.copyOf(runtimeEntityRefs == null ? List.of() : runtimeEntityRefs);
        if (legacyEntityRefs.stream().anyMatch(value -> value == null || !value.contains(":"))
                || runtimeEntityRefs.stream().anyMatch(value -> value == null || !value.contains(":"))) {
            throw new IllegalArgumentException("fixture entity refs must be typed refs");
        }
    }
}
