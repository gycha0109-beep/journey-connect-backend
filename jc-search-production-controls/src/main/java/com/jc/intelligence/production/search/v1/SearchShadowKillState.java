package com.jc.intelligence.production.search.v1;

import com.jc.intelligence.contract.support.WireValue;

public enum SearchShadowKillState implements WireValue {
    KILLED("killed"), ENABLED("enabled");
    private final String wireValue;
    SearchShadowKillState(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
}
