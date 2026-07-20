package com.jc.intelligence.production.search.v1;

import com.jc.intelligence.contract.support.WireValue;

public enum ProjectionVisibilityStatus implements WireValue {
    PUBLIC("public"), NON_PUBLIC("non_public"), UNKNOWN("unknown");
    private final String wireValue;
    ProjectionVisibilityStatus(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
}
