package com.jc.intelligence.contract.v1.search;

import com.jc.intelligence.contract.support.WireValue;

public enum SearchSortType implements WireValue {
    RELEVANCE("relevance"), RECENT("recent"), POPULAR("popular"), DISTANCE("distance");
    private final String wireValue;
    SearchSortType(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
    public static SearchSortType fromWire(String value) {
        for (SearchSortType item : values()) if (item.wireValue.equals(value)) return item;
        throw new IllegalArgumentException("Unknown search sort type: " + value);
    }
}
