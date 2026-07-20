package com.jc.intelligence.wiring.search.v1;

import com.jc.intelligence.contract.support.WireValue;

public enum SearchShadowLogStatus implements WireValue {
    ACCEPTED("accepted"), SKIPPED("skipped"), FAILED("failed");
    private final String wireValue;
    SearchShadowLogStatus(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
}
