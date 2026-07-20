package com.jc.intelligence.production.search.v1;

import com.jc.intelligence.contract.support.WireValue;

public enum SearchShadowEvidenceStatus implements WireValue {
    COMPLETED("completed"), SKIPPED("skipped"), KILLED("killed"), INPUT_UNAVAILABLE("input_unavailable"), TIMED_OUT("timed_out"), REJECTED("rejected"), CIRCUIT_OPEN("circuit_open"), RUNTIME_FAILED("runtime_failed"), COMPARISON_FAILED("comparison_failed");
    private final String wireValue;
    SearchShadowEvidenceStatus(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
}
