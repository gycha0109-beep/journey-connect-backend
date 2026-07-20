package com.jc.intelligence.contract.v1.inference;

import com.jc.intelligence.contract.support.WireValue;

public enum ModelType implements WireValue {
    LLM("llm"),
    ML_MODEL("ml_model"),
    EMBEDDING("embedding"),
    RERANKER("reranker");

    private final String wireValue;

    ModelType(String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wireValue() {
        return wireValue;
    }

    public static ModelType fromWire(String value) {
        for (ModelType candidate : values()) {
            if (candidate.wireValue.equals(value)) {
                return candidate;
            }
        }
        throw com.jc.intelligence.contract.v1.validation.ContractChecks.invalid(
                com.jc.intelligence.contract.v1.validation.IntelligenceValidationErrorCode
                        .INTELLIGENCE_ENUM_VALUE_INVALID,
                "Unknown model type: " + value);
    }
}
