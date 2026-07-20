package com.jc.intelligence.integration.search.v1;

import com.jc.intelligence.contract.support.WireValue;

public enum SearchShadowSeverity implements WireValue {
    INFO("info"),
    WARNING("warning"),
    ERROR("error"),
    NOT_COMPARABLE("not_comparable");

    private final String wireValue;
    SearchShadowSeverity(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
}
