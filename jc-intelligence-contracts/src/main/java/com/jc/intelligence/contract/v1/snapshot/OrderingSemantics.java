package com.jc.intelligence.contract.v1.snapshot;

import com.jc.intelligence.contract.support.WireValue;

public enum OrderingSemantics implements WireValue {
    ORDERED("ordered"),
    UNORDERED("unordered"),
    DOMAIN_DEFINED("domain_defined");

    private final String wireValue;

    OrderingSemantics(String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wireValue() {
        return wireValue;
    }

    public static OrderingSemantics fromWire(String value) {
        for (OrderingSemantics candidate : values()) {
            if (candidate.wireValue.equals(value)) {
                return candidate;
            }
        }
        throw com.jc.intelligence.contract.v1.validation.ContractChecks.invalid(
                com.jc.intelligence.contract.v1.validation.IntelligenceValidationErrorCode
                        .INTELLIGENCE_ENUM_VALUE_INVALID,
                "Unknown ordering semantics: " + value);
    }
}
