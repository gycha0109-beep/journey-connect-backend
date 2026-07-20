package com.jc.intelligence.contract.v1.search;

import com.jc.intelligence.contract.support.WireValue;

public enum SearchQueryMode implements WireValue {
    TEXT_QUERY("text_query"),
    BROWSE("browse");

    private final String wireValue;

    SearchQueryMode(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
    public static SearchQueryMode fromWire(String value) {
        for (SearchQueryMode item : values()) if (item.wireValue.equals(value)) return item;
        throw new IllegalArgumentException("Unknown search query mode: " + value);
    }
}
