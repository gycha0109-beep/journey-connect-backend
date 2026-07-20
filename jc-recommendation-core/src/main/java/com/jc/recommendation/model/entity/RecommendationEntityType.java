package com.jc.recommendation.model.entity;

public enum RecommendationEntityType {
    POST("post"),
    JOURNEY("journey"),
    PLACE("place"),
    CREW("crew"),
    USER("user");

    private final String wireValue;

    RecommendationEntityType(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
