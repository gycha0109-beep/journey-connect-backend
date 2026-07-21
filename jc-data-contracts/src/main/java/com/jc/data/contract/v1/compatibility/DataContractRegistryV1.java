package com.jc.data.contract.v1.compatibility;

import com.jc.data.contract.v1.validation.ContractIdValidatorV1;
import java.util.List;

public final class DataContractRegistryV1 {
    private static final List<String> IDS = List.of(
            "dp-1-event-domain-types-validation-v1",
            "client-event-command-v1",
            "platform-event-v1",
            "behavior-event-taxonomy-v1",
            "platform-event-canonical-json-v1",
            "data-event-consumer-v1",
            "event-idempotency-fingerprint-v1",
            "dp-1-handoff-v1");

    static {
        if (!ContractIdValidatorV1.validateUnique(IDS).isValid()) {
            throw new IllegalStateException("Data contract registry contains invalid or duplicate IDs");
        }
    }

    private DataContractRegistryV1() {
    }

    public static List<String> contractIds() {
        return IDS;
    }
}
