package com.jc.intelligence.contract.v1.search;

import com.jc.intelligence.contract.support.WireValue;

public enum SearchFilterSource implements WireValue {
    USER_SELECTED("user_selected"), SYSTEM("system"), OPERATIONS("operations");
    private final String wireValue;
    SearchFilterSource(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
    public static SearchFilterSource fromWire(String value) {
        for (SearchFilterSource item : values()) if (item.wireValue.equals(value)) return item;
        throw new IllegalArgumentException("Unknown search filter source: " + value);
    }
}
