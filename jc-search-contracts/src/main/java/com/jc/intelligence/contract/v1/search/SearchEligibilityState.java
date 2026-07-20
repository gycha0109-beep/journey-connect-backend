package com.jc.intelligence.contract.v1.search;

import com.jc.intelligence.contract.support.WireValue;

public enum SearchEligibilityState implements WireValue {
    ELIGIBLE("eligible"), INELIGIBLE("ineligible"), UNKNOWN("unknown");
    private final String wireValue;
    SearchEligibilityState(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
    public static SearchEligibilityState fromWire(String value) {
        for (SearchEligibilityState item : values()) if (item.wireValue.equals(value)) return item;
        throw new IllegalArgumentException("Unknown search eligibility state: " + value);
    }
}
