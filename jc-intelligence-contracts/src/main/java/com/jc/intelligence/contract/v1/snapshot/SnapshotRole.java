package com.jc.intelligence.contract.v1.snapshot;

import com.jc.intelligence.contract.support.WireValue;

public enum SnapshotRole implements WireValue {
    INPUT("input"),
    CANDIDATE("candidate"),
    OUTPUT("output"),
    EXPOSURE_EVIDENCE("exposure_evidence");

    private final String wireValue;

    SnapshotRole(String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wireValue() {
        return wireValue;
    }

    public static SnapshotRole fromWire(String value) {
        for (SnapshotRole candidate : values()) {
            if (candidate.wireValue.equals(value)) {
                return candidate;
            }
        }
        throw com.jc.intelligence.contract.v1.validation.ContractChecks.invalid(
                com.jc.intelligence.contract.v1.validation.IntelligenceValidationErrorCode
                        .INTELLIGENCE_ENUM_VALUE_INVALID,
                "Unknown snapshot role: " + value);
    }
}
