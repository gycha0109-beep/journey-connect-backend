package com.jc.data.contract.v1.adapter.recommendation;

public enum RecommendationP0MappingStatus {
    MAPPED_SHADOW("mapped_shadow"),
    UNSUPPORTED("unsupported"),
    QUARANTINED("quarantined");

    private final String wireValue;

    RecommendationP0MappingStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
