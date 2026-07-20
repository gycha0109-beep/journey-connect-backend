package com.jc.intelligence.wiring.search.v1;

import com.jc.intelligence.contract.support.WireValue;

public enum SearchShadowCircuitState implements WireValue {
    CLOSED("closed"), OPEN("open"), HALF_OPEN("half_open");
    private final String wireValue;
    SearchShadowCircuitState(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
}
