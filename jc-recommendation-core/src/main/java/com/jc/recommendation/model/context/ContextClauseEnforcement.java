package com.jc.recommendation.model.context;

public enum ContextClauseEnforcement {
    HARD_REQUIRED("hard_required"), HARD_EXCLUDED("hard_excluded"),
    SOFT_PREFERRED("soft_preferred"), SOFT_AVOIDED("soft_avoided");
    private final String wireValue;
    ContextClauseEnforcement(String wireValue) { this.wireValue = wireValue; }
    public String wireValue() { return wireValue; }
    public boolean isHard() { return this == HARD_REQUIRED || this == HARD_EXCLUDED; }
    public boolean isSoft() { return this == SOFT_PREFERRED || this == SOFT_AVOIDED; }
}
