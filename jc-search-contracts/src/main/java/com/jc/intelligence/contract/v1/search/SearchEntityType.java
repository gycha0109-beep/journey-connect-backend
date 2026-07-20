package com.jc.intelligence.contract.v1.search;

import com.jc.intelligence.contract.support.WireValue;

public enum SearchEntityType implements WireValue {
    POST("post"), REGION("region"), TAG("tag"), PLACE("place"), USER("user"), CREW("crew");
    private final String wireValue;
    SearchEntityType(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
    public static SearchEntityType fromWire(String value) {
        for (SearchEntityType item : values()) if (item.wireValue.equals(value)) return item;
        throw new IllegalArgumentException("Unknown search entity type: " + value);
    }
}
