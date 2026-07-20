package com.jc.intelligence.contract.v1.search;

import com.jc.intelligence.contract.support.WireValue;

public enum SearchFailureCode implements WireValue {
    INVALID_REQUEST("invalid_request"),
    UNSUPPORTED_FILTER("unsupported_filter"),
    RETRIEVAL_UNAVAILABLE("retrieval_unavailable"),
    PROVIDER_UNAVAILABLE("provider_unavailable"),
    RANKING_FAILED("ranking_failed"),
    SNAPSHOT_FAILED("snapshot_failed"),
    CURSOR_INVALID("cursor_invalid"),
    CURSOR_EXPIRED("cursor_expired"),
    CURSOR_MISMATCH("cursor_mismatch"),
    CURSOR_STALE("cursor_stale"),
    VISIBILITY_DEPENDENCY_UNAVAILABLE("visibility_dependency_unavailable");
    private final String wireValue;
    SearchFailureCode(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
    public static SearchFailureCode fromWire(String value) {
        for (SearchFailureCode item : values()) if (item.wireValue.equals(value)) return item;
        throw new IllegalArgumentException("Unknown search failure code: " + value);
    }
}
