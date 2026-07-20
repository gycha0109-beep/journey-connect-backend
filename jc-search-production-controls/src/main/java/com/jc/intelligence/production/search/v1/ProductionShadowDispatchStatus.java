package com.jc.intelligence.production.search.v1;

import com.jc.intelligence.contract.support.WireValue;

public enum ProductionShadowDispatchStatus implements WireValue {
    KILLED("killed"), COHORT_REJECTED("cohort_rejected"), NOT_SAMPLED("not_sampled"), SUBMITTED("submitted"), QUEUE_FULL("queue_full"), EXECUTOR_UNAVAILABLE("executor_unavailable"), REJECTED("rejected");
    private final String wireValue;
    ProductionShadowDispatchStatus(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
}
