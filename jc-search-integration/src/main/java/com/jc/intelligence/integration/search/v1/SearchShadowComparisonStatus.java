package com.jc.intelligence.integration.search.v1;

import com.jc.intelligence.contract.support.WireValue;

public enum SearchShadowComparisonStatus implements WireValue {
    COMPARED("compared"),
    NOT_COMPARABLE("not_comparable"),
    FAILED("failed");

    private final String wireValue;
    SearchShadowComparisonStatus(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
}
