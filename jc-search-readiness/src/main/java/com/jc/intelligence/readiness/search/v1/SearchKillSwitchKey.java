package com.jc.intelligence.readiness.search.v1;

import com.jc.intelligence.contract.support.WireValue;

public enum SearchKillSwitchKey implements WireValue {
    GLOBAL_DISABLED("global_disabled"), PROFILE_DISABLED("profile_disabled"), SAMPLE_RATE_ZERO("sample_rate_zero"),
    CIRCUIT_OPEN("circuit_open"), EXECUTOR_UNAVAILABLE("executor_unavailable");
    private final String wireValue;
    SearchKillSwitchKey(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
}
