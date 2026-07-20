package com.jc.recommendation.model.diversity;

public enum DiversityDimension {
    DUPLICATE_GROUP("duplicate_group"), AUTHOR("author"), REGION("region"), THEME("theme");
    private final String wireValue;
    DiversityDimension(String wireValue) { this.wireValue = wireValue; }
    public String wireValue() { return wireValue; }
}
