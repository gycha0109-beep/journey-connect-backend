package com.jc.intelligence.production.search.v1;

import com.jc.intelligence.contract.support.WireValue;

public enum SearchProjectionAvailabilityStatus implements WireValue {
    AVAILABLE("available"), EMPTY("empty"), UNAVAILABLE("unavailable"), STALE("stale"), UNSUPPORTED_SCHEMA("unsupported_schema"), UNSUPPORTED_POLICY("unsupported_policy");
    private final String wireValue;
    SearchProjectionAvailabilityStatus(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
}
