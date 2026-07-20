package com.jc.recommendation.model.entity;

public enum RecommendationEntityStatus {
    ACTIVE("active"),
    HIDDEN("hidden"),
    DELETED("deleted");

    private final String wireValue;

    RecommendationEntityStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
