package com.jc.recommendation.model.popularity;

public enum PopularityNotApplicableReason {
    UNSUPPORTED_ENTITY_TYPE("unsupported_entity_type"),
    MISSING_SNAPSHOT("missing_snapshot"),
    UNTRUSTED_SNAPSHOT("untrusted_snapshot"),
    INSUFFICIENT_UNIQUE_EXPOSURE("insufficient_unique_exposure");

    private final String wireValue;

    PopularityNotApplicableReason(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
