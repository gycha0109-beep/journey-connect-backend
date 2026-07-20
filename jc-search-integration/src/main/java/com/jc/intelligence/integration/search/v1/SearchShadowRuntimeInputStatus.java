package com.jc.intelligence.integration.search.v1;

import com.jc.intelligence.contract.support.WireValue;

public enum SearchShadowRuntimeInputStatus implements WireValue {
    AVAILABLE("available"),
    UNAVAILABLE("unavailable"),
    UNSUPPORTED("unsupported"),
    INVALID("invalid");

    private final String wireValue;
    SearchShadowRuntimeInputStatus(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
}
