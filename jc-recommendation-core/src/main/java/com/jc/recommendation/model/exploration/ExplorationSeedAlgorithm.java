package com.jc.recommendation.model.exploration;

public enum ExplorationSeedAlgorithm {
    FNV1A32_UTF8_V1("fnv1a32_utf8_v1");

    private final String wireValue;

    ExplorationSeedAlgorithm(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
