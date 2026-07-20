package com.jc.recommendation.model.popularity;

public enum PopularityTrustStatus {
    TRUSTED("trusted"),
    PARTIAL("partial"),
    REJECTED("rejected");

    private final String wireValue;

    PopularityTrustStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
