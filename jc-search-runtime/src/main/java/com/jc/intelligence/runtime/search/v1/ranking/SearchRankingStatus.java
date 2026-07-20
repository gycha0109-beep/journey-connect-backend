package com.jc.intelligence.runtime.search.v1.ranking;

import com.jc.intelligence.contract.support.WireValue;

public enum SearchRankingStatus implements WireValue {
    SUCCESS("success"), FAILED("failed");
    private final String wireValue;
    SearchRankingStatus(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
}
