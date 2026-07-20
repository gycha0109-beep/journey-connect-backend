package com.jc.intelligence.contract.v1.version;

import com.jc.intelligence.contract.v1.validation.ContractChecks;

public record ContractId(String value) {
    public ContractId {
        value = ContractChecks.requireContractId(value, "contractId");
    }

    @Override
    public String toString() {
        return value;
    }
}
