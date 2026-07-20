package com.jc.recommendation.model.diversity;

public enum DiversitySelectionReason {
    BASE_ORDER_PRESERVED("base_order_preserved"),
    STRICT_DIVERSITY("strict_diversity"),
    RELAXED_DIVERSITY("relaxed_diversity"),
    MOVEMENT_BOUND_FORCED("movement_bound_forced");
    private final String wireValue;
    DiversitySelectionReason(String wireValue) { this.wireValue = wireValue; }
    public String wireValue() { return wireValue; }
}
