package com.jc.intelligence.contract.v1.search;

import com.jc.intelligence.contract.support.WireValue;

public enum RetrievalSource implements WireValue {
    DATABASE_POST("database_post"), DATABASE_REGION("database_region"), DATABASE_TAG("database_tag"),
    DATABASE_PLACE("database_place"), DATABASE_USER("database_user"), DATABASE_CREW("database_crew"),
    SEARCH_INDEX("search_index"), EXTERNAL_PROVIDER("external_provider");
    private final String wireValue;
    RetrievalSource(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
    public static RetrievalSource fromWire(String value) {
        for (RetrievalSource item : values()) if (item.wireValue.equals(value)) return item;
        throw new IllegalArgumentException("Unknown retrieval source: " + value);
    }
}
