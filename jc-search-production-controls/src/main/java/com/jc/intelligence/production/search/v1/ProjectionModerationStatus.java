package com.jc.intelligence.production.search.v1;

import com.jc.intelligence.contract.support.WireValue;

public enum ProjectionModerationStatus implements WireValue {
    ELIGIBLE("eligible"), BLOCKED("blocked"), UNKNOWN("unknown");
    private final String wireValue;
    ProjectionModerationStatus(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
}
