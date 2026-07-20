package com.jc.intelligence.wiring.search.v1;

import com.jc.intelligence.contract.support.WireValue;

public enum SearchShadowExecutorStatus implements WireValue {
    COMPLETED("completed"), REJECTED("rejected"), QUEUE_FULL("queue_full"), EXECUTOR_UNAVAILABLE("executor_unavailable"), TIMED_OUT("timed_out"), CANCELLED("cancelled"), FAILED("failed");
    private final String wireValue;
    SearchShadowExecutorStatus(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
}
