package com.jc.intelligence.runtime.search.v1;

import com.jc.intelligence.contract.support.WireValue;

public enum SearchRuntimeFallbackCode implements WireValue {
    SOURCE_RANK_ORDERING("source_rank_ordering");

    private final String wireValue;

    SearchRuntimeFallbackCode(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
}
