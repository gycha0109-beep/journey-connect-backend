package com.jc.intelligence.contract.v1.version;

import java.util.Map;

public final class IntelligenceContractIds {
    public static final ContractId INTELLIGENCE_RUN = new ContractId("intelligence-run-v1");
    public static final ContractId INTELLIGENCE_INPUT_SNAPSHOT =
            new ContractId("intelligence-input-snapshot-v1");
    public static final ContractId INTELLIGENCE_CANDIDATE_SNAPSHOT =
            new ContractId("intelligence-candidate-snapshot-v1");
    public static final ContractId INTELLIGENCE_OUTPUT_SNAPSHOT =
            new ContractId("intelligence-output-snapshot-v1");
    public static final ContractId INTELLIGENCE_FEATURE_VALUE =
            new ContractId("intelligence-feature-value-v1");
    public static final ContractId INTELLIGENCE_EXPLANATION =
            new ContractId("intelligence-explanation-v1");
    public static final ContractId MODEL_INFERENCE_RECORD =
            new ContractId("model-inference-record-v1");

    private static final Map<String, ContractId> RESERVED = Map.of(
            INTELLIGENCE_RUN.value(), INTELLIGENCE_RUN,
            INTELLIGENCE_INPUT_SNAPSHOT.value(), INTELLIGENCE_INPUT_SNAPSHOT,
            INTELLIGENCE_CANDIDATE_SNAPSHOT.value(), INTELLIGENCE_CANDIDATE_SNAPSHOT,
            INTELLIGENCE_OUTPUT_SNAPSHOT.value(), INTELLIGENCE_OUTPUT_SNAPSHOT,
            INTELLIGENCE_FEATURE_VALUE.value(), INTELLIGENCE_FEATURE_VALUE,
            INTELLIGENCE_EXPLANATION.value(), INTELLIGENCE_EXPLANATION,
            MODEL_INFERENCE_RECORD.value(), MODEL_INFERENCE_RECORD);

    private IntelligenceContractIds() {
    }

    public static ContractId requireReserved(String value) {
        ContractId id = new ContractId(value);
        ContractId registered = RESERVED.get(id.value());
        if (registered == null) {
            throw com.jc.intelligence.contract.v1.validation.ContractChecks.invalid(
                    com.jc.intelligence.contract.v1.validation.IntelligenceValidationErrorCode
                            .INTELLIGENCE_CONTRACT_ID_INVALID,
                    "contractId is not reserved by SC-1: " + value);
        }
        return registered;
    }

    public static Map<String, ContractId> reserved() {
        return RESERVED;
    }
}
