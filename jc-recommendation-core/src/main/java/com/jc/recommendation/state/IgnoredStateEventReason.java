package com.jc.recommendation.state;

public enum IgnoredStateEventReason {
    DUPLICATE_IDEMPOTENCY("duplicate_idempotency"),
    DUPLICATE_STATE("duplicate_state"),
    INVALID_INVERSE_STATE("invalid_inverse_state");

    private final String wireValue;

    IgnoredStateEventReason(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
