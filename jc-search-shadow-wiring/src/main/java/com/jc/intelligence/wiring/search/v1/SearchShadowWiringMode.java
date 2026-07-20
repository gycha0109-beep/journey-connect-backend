package com.jc.intelligence.wiring.search.v1;

import com.jc.intelligence.contract.support.WireValue;

public enum SearchShadowWiringMode implements WireValue {
    DISABLED("disabled"), TEST_ONLY("test_only"), SHADOW_CANDIDATE("shadow_candidate");
    private final String wireValue;
    SearchShadowWiringMode(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }
    public static SearchShadowWiringMode parseOrDisabled(String value) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) return DISABLED;
        for (SearchShadowWiringMode mode : values()) if (mode.wireValue.equals(value.trim())) return mode;
        return DISABLED;
    }
}
