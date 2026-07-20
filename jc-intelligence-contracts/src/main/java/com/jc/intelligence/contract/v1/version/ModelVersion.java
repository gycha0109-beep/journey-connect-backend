package com.jc.intelligence.contract.v1.version;

import com.jc.intelligence.contract.v1.validation.ContractChecks;

public record ModelVersion(String value) {
    public ModelVersion {
        value = ContractChecks.requireVersion(value, "modelVersion");
    }

    @Override
    public String toString() {
        return value;
    }
}
