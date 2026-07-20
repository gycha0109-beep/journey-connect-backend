package com.jc.intelligence.contract.v1.search;

import com.jc.intelligence.contract.support.WireValue;

public enum SearchFallbackCode implements WireValue {
    RETRIEVAL_UNAVAILABLE("retrieval_unavailable"),
    PROVIDER_UNAVAILABLE("provider_unavailable"),
    RANKING_FAILED("ranking_failed"),
    VISIBILITY_DEPENDENCY_UNAVAILABLE("visibility_dependency_unavailable"),
    INSUFFICIENT_CANDIDATES("insufficient_candidates");
    private final String wireValue;
    SearchFallbackCode(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
    public static SearchFallbackCode fromWire(String value) {
        for (SearchFallbackCode item : values()) if (item.wireValue.equals(value)) return item;
        throw new IllegalArgumentException("Unknown search fallback code: " + value);
    }
}
