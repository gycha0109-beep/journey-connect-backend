package com.jc.intelligence.runtime.search.v1.port;

import com.jc.intelligence.contract.support.WireValue;

public enum SearchRetrievalStatus implements WireValue {
    SUCCESS("success"), UNAVAILABLE("unavailable"), FAILED("failed");
    private final String wireValue;
    SearchRetrievalStatus(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
}
