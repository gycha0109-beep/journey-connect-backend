package com.jc.intelligence.contract.v1.search;

import com.jc.intelligence.contract.support.WireValue;

public enum SearchFilterType implements WireValue {
    ENTITY_SCOPE("entity_scope", false),
    REGION("region", true),
    REGION_DESCENDANT("region_descendant", true),
    TAG("tag", true),
    PLACE_CATEGORY("place_category", true),
    PUBLISHED_AFTER("published_after", false),
    PUBLISHED_BEFORE("published_before", false),
    LANGUAGE("language", false);
    private final String wireValue;
    private final boolean multiValued;
    SearchFilterType(String wireValue, boolean multiValued) {
        this.wireValue = wireValue;
        this.multiValued = multiValued;
    }
    @Override public String wireValue() { return wireValue; }
    public boolean multiValued() { return multiValued; }
    public static SearchFilterType fromWire(String value) {
        for (SearchFilterType item : values()) if (item.wireValue.equals(value)) return item;
        throw new IllegalArgumentException("Unknown search filter type: " + value);
    }
}
