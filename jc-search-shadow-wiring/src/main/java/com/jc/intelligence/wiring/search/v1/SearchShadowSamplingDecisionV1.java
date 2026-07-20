package com.jc.intelligence.wiring.search.v1;

public record SearchShadowSamplingDecisionV1(boolean included, int bucket, int basisPoints, String decisionFingerprint) {
    public SearchShadowSamplingDecisionV1 {
        if (bucket < 0 || bucket >= 10_000 || basisPoints < 0 || basisPoints > 10_000) {
            throw new IllegalArgumentException("sampling values are out of range");
        }
        if (decisionFingerprint == null || !decisionFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("decisionFingerprint must be lowercase SHA-256");
        }
        if (included != (bucket < basisPoints)) throw new IllegalArgumentException("included must match deterministic bucket rule");
    }
}
