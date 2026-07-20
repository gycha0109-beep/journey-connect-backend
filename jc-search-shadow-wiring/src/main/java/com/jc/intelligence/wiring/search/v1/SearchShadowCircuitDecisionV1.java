package com.jc.intelligence.wiring.search.v1;

public record SearchShadowCircuitDecisionV1(SearchShadowCircuitState state, boolean permitted, String reasonCode) {
    public SearchShadowCircuitDecisionV1 {
        if (state == null || reasonCode == null || !reasonCode.matches("[a-z][a-z0-9_]{0,63}")) {
            throw new IllegalArgumentException("circuit decision fields are invalid");
        }
        if (state == SearchShadowCircuitState.OPEN && permitted) throw new IllegalArgumentException("open circuit cannot permit dispatch");
    }
}
