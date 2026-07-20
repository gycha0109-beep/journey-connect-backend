package com.jc.intelligence.wiring.search.v1;

@FunctionalInterface
public interface SearchShadowCircuitBreaker {
    SearchShadowCircuitDecisionV1 evaluate(String correlationFingerprint);
}
