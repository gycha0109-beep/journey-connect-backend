package com.jc.intelligence.integration.search.v1;

import com.jc.intelligence.contract.support.WireValue;

public enum SearchShadowMode implements WireValue {
    DISABLED("disabled"),
    TEST_ONLY("test_only"),
    SHADOW_ENABLED("shadow_enabled");

    private final String wireValue;

    SearchShadowMode(String wireValue) { this.wireValue = wireValue; }
    @Override public String wireValue() { return wireValue; }

    public static SearchShadowMode fromWireOrDisabled(String value) {
        if (value == null) return DISABLED;
        for (SearchShadowMode item : values()) {
            if (item.wireValue.equals(value)) return item;
        }
        return DISABLED;
    }
}
