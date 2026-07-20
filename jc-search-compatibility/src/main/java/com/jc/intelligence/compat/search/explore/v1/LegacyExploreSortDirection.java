package com.jc.intelligence.compat.search.explore.v1;

import com.jc.intelligence.contract.support.WireValue;

public enum LegacyExploreSortDirection implements WireValue {
    ASC("asc"), DESC("desc");
    private final String wireValue;
    LegacyExploreSortDirection(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
    public static LegacyExploreSortDirection fromWire(String value) {
        for (LegacyExploreSortDirection item : values()) if (item.wireValue.equals(value)) return item;
        throw new IllegalArgumentException("Unknown sort direction: " + value);
    }
}
