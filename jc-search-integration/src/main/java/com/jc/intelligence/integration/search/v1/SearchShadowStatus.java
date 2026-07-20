package com.jc.intelligence.integration.search.v1;

import com.jc.intelligence.contract.support.WireValue;

public enum SearchShadowStatus implements WireValue {
    DISABLED("disabled"),
    COMPARED("compared"),
    NOT_COMPARABLE("not_comparable"),
    INPUT_UNAVAILABLE("input_unavailable"),
    INPUT_UNSUPPORTED("input_unsupported"),
    INVALID_INPUT("invalid_input"),
    TIMED_OUT("timed_out"),
    RUNTIME_FAILED("runtime_failed"),
    COMPARISON_FAILED("comparison_failed");

    private final String wireValue;
    SearchShadowStatus(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
}
