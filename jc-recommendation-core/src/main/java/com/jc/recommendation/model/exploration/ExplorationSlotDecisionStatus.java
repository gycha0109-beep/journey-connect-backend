package com.jc.recommendation.model.exploration;

public enum ExplorationSlotDecisionStatus {
    INSERTED("inserted"),
    SKIPPED_DEPTH("skipped_depth"),
    SKIPPED_NO_CANDIDATE("skipped_no_candidate"),
    SKIPPED_DIVERSITY("skipped_diversity");

    private final String wireValue;

    ExplorationSlotDecisionStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
