package com.jc.intelligence.contract.v1.version;

import com.jc.intelligence.contract.v1.validation.ContractChecks;

public record ProducerBuildId(String value) {
    public ProducerBuildId {
        value = ContractChecks.requireBuildId(value, "producerBuildId");
    }

    @Override
    public String toString() {
        return value;
    }
}
