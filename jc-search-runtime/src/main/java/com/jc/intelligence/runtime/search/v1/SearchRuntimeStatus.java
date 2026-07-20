package com.jc.intelligence.runtime.search.v1;

import com.jc.intelligence.contract.support.WireValue;

public enum SearchRuntimeStatus implements WireValue {
    SUCCESS("success"),
    NO_RESULTS("no_results"),
    FALLBACK("fallback"),
    FAILED("failed"),
    INVALID_REQUEST("invalid_request"),
    DEPENDENCY_UNAVAILABLE("dependency_unavailable");

    private final String wireValue;

    SearchRuntimeStatus(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
}
