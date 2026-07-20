package com.jc.intelligence.contract.v1.version;

import com.jc.intelligence.contract.v1.validation.ContractChecks;

public record PromptVersion(String value) {
    public PromptVersion {
        value = ContractChecks.requireVersion(value, "promptVersion");
    }

    @Override
    public String toString() {
        return value;
    }
}
