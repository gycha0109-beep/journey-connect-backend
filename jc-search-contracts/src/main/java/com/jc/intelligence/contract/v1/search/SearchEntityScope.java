package com.jc.intelligence.contract.v1.search;

import com.jc.intelligence.contract.support.WireValue;

public enum SearchEntityScope implements WireValue {
    POST("post"), REGION("region"), TAG("tag"), PLACE("place"), USER("user"), CREW("crew"), ALL("all");
    private final String wireValue;
    SearchEntityScope(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
    public static SearchEntityScope fromWire(String value) {
        for (SearchEntityScope item : values()) if (item.wireValue.equals(value)) return item;
        throw new IllegalArgumentException("Unknown search entity scope: " + value);
    }
    public boolean accepts(SearchEntityType type) {
        return this == ALL || wireValue.equals(type.wireValue());
    }
}
