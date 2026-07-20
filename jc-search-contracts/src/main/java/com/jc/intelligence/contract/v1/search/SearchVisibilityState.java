package com.jc.intelligence.contract.v1.search;

import com.jc.intelligence.contract.support.WireValue;

public enum SearchVisibilityState implements WireValue {
    VISIBLE("visible"), HIDDEN("hidden"), REMOVED("removed"), UNKNOWN("unknown");
    private final String wireValue;
    SearchVisibilityState(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
    public static SearchVisibilityState fromWire(String value) {
        for (SearchVisibilityState item : values()) if (item.wireValue.equals(value)) return item;
        throw new IllegalArgumentException("Unknown search visibility state: " + value);
    }
}
