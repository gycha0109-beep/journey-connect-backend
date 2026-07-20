package com.jc.intelligence.contract.v1.search;

import com.jc.intelligence.contract.support.WireValue;

public enum SearchExplanationReason implements WireValue {
    QUERY_MATCH("query_match"), REGION_MATCH("region_match"), TAG_MATCH("tag_match"),
    POPULARITY_SIGNAL("popularity_signal"), FRESHNESS_SIGNAL("freshness_signal");
    private final String wireValue;
    SearchExplanationReason(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
    public static SearchExplanationReason fromWire(String value) {
        for (SearchExplanationReason item : values()) if (item.wireValue.equals(value)) return item;
        throw new IllegalArgumentException("Unknown search explanation reason: " + value);
    }
}
