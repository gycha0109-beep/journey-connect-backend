package com.jc.intelligence.contract.v1.search;

import com.jc.intelligence.contract.support.WireValue;

public enum SearchSurface implements WireValue {
    GLOBAL_SEARCH("global_search"),
    EXPLORE("explore"),
    REGION_SEARCH("region_search"),
    TAG_SEARCH("tag_search"),
    PLACE_SEARCH("place_search"),
    USER_SEARCH("user_search"),
    CREW_SEARCH("crew_search");
    private final String wireValue;
    SearchSurface(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
    public static SearchSurface fromWire(String value) {
        for (SearchSurface item : values()) if (item.wireValue.equals(value)) return item;
        throw new IllegalArgumentException("Unknown search surface: " + value);
    }
}
