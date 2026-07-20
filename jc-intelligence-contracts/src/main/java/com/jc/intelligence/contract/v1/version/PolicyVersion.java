package com.jc.intelligence.contract.v1.version;

import com.jc.intelligence.contract.v1.validation.ContractChecks;

public record PolicyVersion(String value) {
    public PolicyVersion {
        value = ContractChecks.requireVersion(value, "policyVersion");
    }

    @Override
    public String toString() {
        return value;
    }
}
