package com.jc.intelligence.contract.v1.authority;

import com.jc.intelligence.contract.support.WireValue;

public enum ExposureSourceStatus implements WireValue {
    ACTIVE("active"),
    PROTECTED_AUTHORITY("protected_authority"),
    RESERVED("reserved");

    private final String wireValue;

    ExposureSourceStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    @Override
    public String wireValue() {
        return wireValue;
    }
}
