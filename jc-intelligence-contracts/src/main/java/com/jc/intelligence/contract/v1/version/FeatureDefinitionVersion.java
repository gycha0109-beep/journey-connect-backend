package com.jc.intelligence.contract.v1.version;

import com.jc.intelligence.contract.v1.validation.ContractChecks;

public record FeatureDefinitionVersion(String value) {
    public FeatureDefinitionVersion {
        value = ContractChecks.requireVersion(value, "featureDefinitionVersion");
    }

    @Override
    public String toString() {
        return value;
    }
}
