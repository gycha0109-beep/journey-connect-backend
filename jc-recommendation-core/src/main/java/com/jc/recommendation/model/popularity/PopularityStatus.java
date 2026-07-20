package com.jc.recommendation.model.popularity;

public enum PopularityStatus {
    SCORED("scored"),
    NOT_APPLICABLE("not_applicable");

    private final String wireValue;

    PopularityStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
