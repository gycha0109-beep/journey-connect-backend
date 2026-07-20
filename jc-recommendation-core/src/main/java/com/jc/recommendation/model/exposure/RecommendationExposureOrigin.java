package com.jc.recommendation.model.exposure;

public enum RecommendationExposureOrigin {
    PERSONALIZED("personalized"), EXPLORATION("exploration");
    private final String wireValue;
    RecommendationExposureOrigin(String wireValue) { this.wireValue = wireValue; }
    public String wireValue() { return wireValue; }
}
