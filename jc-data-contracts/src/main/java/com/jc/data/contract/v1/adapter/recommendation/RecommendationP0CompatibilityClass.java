package com.jc.data.contract.v1.adapter.recommendation;

public enum RecommendationP0CompatibilityClass {
    EXACT_COMPATIBLE("exact_compatible"),
    SEMANTIC_COMPATIBLE("semantic_compatible"),
    UNSUPPORTED("unsupported");

    private final String wireValue;

    RecommendationP0CompatibilityClass(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
