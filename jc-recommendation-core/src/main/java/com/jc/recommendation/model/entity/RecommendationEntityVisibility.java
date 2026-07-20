package com.jc.recommendation.model.entity;

public enum RecommendationEntityVisibility {
    PUBLIC("public"),
    FOLLOWERS("followers"),
    PRIVATE("private");

    private final String wireValue;

    RecommendationEntityVisibility(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
