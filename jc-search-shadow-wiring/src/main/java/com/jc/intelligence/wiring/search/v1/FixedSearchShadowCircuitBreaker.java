package com.jc.intelligence.wiring.search.v1;

import java.util.Objects;

public final class FixedSearchShadowCircuitBreaker implements SearchShadowCircuitBreaker {
    private final SearchShadowCircuitState state;
    private final boolean halfOpenTrialAllowed;
    public FixedSearchShadowCircuitBreaker(SearchShadowCircuitState state, boolean halfOpenTrialAllowed) {
        this.state = Objects.requireNonNull(state, "state");
        this.halfOpenTrialAllowed = halfOpenTrialAllowed;
    }
    @Override public SearchShadowCircuitDecisionV1 evaluate(String correlationFingerprint) {
        if (correlationFingerprint == null || !correlationFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("correlationFingerprint must be lowercase SHA-256");
        }
        return switch (state) {
            case CLOSED -> new SearchShadowCircuitDecisionV1(state, true, "closed");
            case OPEN -> new SearchShadowCircuitDecisionV1(state, false, "circuit_open");
            case HALF_OPEN -> new SearchShadowCircuitDecisionV1(state, halfOpenTrialAllowed,
                    halfOpenTrialAllowed ? "half_open_trial" : "half_open_blocked");
        };
    }
}
