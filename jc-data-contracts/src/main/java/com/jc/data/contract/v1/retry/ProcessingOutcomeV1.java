package com.jc.data.contract.v1.retry;

public enum ProcessingOutcomeV1 {
    RETRY_SUCCEEDED("retry_succeeded"),
    RETRY_FAILED("retry_failed"),
    RETRY_SCHEDULED("retry_scheduled"),
    RETRY_EXHAUSTED("retry_exhausted"),
    QUARANTINED("quarantined");

    private final String wireValue;

    ProcessingOutcomeV1(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
