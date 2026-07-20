package com.jc.intelligence.contract.v1.replay;

import com.jc.intelligence.contract.support.WireValue;

public enum ReplayClass implements WireValue {
    EXACT_REPLAY("exact_replay"),
    SEMANTIC_REPLAY("semantic_replay"),
    EVIDENCE_REPLAY("evidence_replay");

    private final String wireValue;

    ReplayClass(String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wireValue() {
        return wireValue;
    }

    public static ReplayClass fromWire(String value) {
        for (ReplayClass candidate : values()) {
            if (candidate.wireValue.equals(value)) {
                return candidate;
            }
        }
        throw com.jc.intelligence.contract.v1.validation.ContractChecks.invalid(
                com.jc.intelligence.contract.v1.validation.IntelligenceValidationErrorCode
                        .INTELLIGENCE_ENUM_VALUE_INVALID,
                "Unknown replay class: " + value);
    }
}
