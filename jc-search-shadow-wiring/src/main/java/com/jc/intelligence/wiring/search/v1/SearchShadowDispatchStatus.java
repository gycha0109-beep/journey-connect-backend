package com.jc.intelligence.wiring.search.v1;

import com.jc.intelligence.contract.support.WireValue;

public enum SearchShadowDispatchStatus implements WireValue {
    DISABLED("disabled"), PROFILE_BLOCKED("profile_blocked"), NOT_SAMPLED("not_sampled"), INPUT_UNAVAILABLE("input_unavailable"), INPUT_UNSUPPORTED("input_unsupported"), INVALID_INPUT("invalid_input"), CANCELLED("cancelled"), SUBMITTED("submitted"), COMPLETED("completed"), REJECTED("rejected"), QUEUE_FULL("queue_full"), EXECUTOR_UNAVAILABLE("executor_unavailable"), TIMED_OUT("timed_out"), CIRCUIT_OPEN("circuit_open"), COMPARISON_FAILED("comparison_failed"), LOGGING_FAILED("logging_failed"), FAILED("failed");
    private final String wireValue;
    SearchShadowDispatchStatus(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
}
