package com.jc.intelligence.compat.search.explore.v1;

import com.jc.intelligence.contract.support.WireValue;

public enum LegacyExploreCompatibilityStatus implements WireValue {
    SUCCESS("success"), MAPPING_FAILURE("mapping_failure"), UNSUPPORTED("unsupported"), INVALID_INPUT("invalid_input");
    private final String wireValue;
    LegacyExploreCompatibilityStatus(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
    public static LegacyExploreCompatibilityStatus fromWire(String value) {
        for (LegacyExploreCompatibilityStatus item : values()) if (item.wireValue.equals(value)) return item;
        throw new IllegalArgumentException("Unknown compatibility status: " + value);
    }
}
