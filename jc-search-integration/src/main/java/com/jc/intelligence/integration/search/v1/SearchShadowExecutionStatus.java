package com.jc.intelligence.integration.search.v1;

import com.jc.intelligence.contract.support.WireValue;

public enum SearchShadowExecutionStatus implements WireValue {
    COMPLETED("completed"),
    TIMED_OUT("timed_out"),
    FAILED("failed");

    private final String wireValue;
    SearchShadowExecutionStatus(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
}
