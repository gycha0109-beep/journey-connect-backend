package com.jc.intelligence.production.search.v1;

import com.jc.intelligence.contract.support.WireValue;

public enum ProjectionDeletionStatus implements WireValue {
    ACTIVE("active"), DELETED("deleted"), UNKNOWN("unknown");
    private final String wireValue;
    ProjectionDeletionStatus(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
}
