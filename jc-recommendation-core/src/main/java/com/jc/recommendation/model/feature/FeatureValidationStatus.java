package com.jc.recommendation.model.feature;

public enum FeatureValidationStatus {
    ACCEPTED("accepted"),
    PENDING("pending"),
    REJECTED("rejected");

    private final String wireValue;

    FeatureValidationStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
