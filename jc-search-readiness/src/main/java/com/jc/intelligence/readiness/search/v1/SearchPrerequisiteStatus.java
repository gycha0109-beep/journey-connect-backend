package com.jc.intelligence.readiness.search.v1;

import com.jc.intelligence.contract.support.WireValue;

public enum SearchPrerequisiteStatus implements WireValue {
    RESOLVED("resolved"), PARTIALLY_RESOLVED("partially_resolved"), UNRESOLVED("unresolved"),
    NOT_REQUIRED_FOR_SHADOW("not_required_for_shadow");
    private final String wireValue;
    SearchPrerequisiteStatus(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
}
