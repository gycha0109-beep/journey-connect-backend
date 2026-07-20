package com.jc.intelligence.contract.v1.version;

import com.jc.intelligence.contract.v1.validation.ContractChecks;

public record SchemaVersion(String value) {
    public SchemaVersion {
        value = ContractChecks.requireVersion(value, "schemaVersion");
    }

    @Override
    public String toString() {
        return value;
    }
}
