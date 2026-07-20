package com.jc.intelligence.wiring.search.v1;

public record SearchShadowWiringAuthorityV1(
        String responseAuthority,
        boolean responseModified,
        boolean persistenceAuthority,
        boolean exposureAuthority,
        boolean releaseGateAuthority,
        boolean metricAuthority,
        boolean productionCursorAuthority,
        boolean productionActivationAuthority,
        boolean apiCutoverAuthority) {
    public SearchShadowWiringAuthorityV1 {
        if (!"legacy".equals(responseAuthority)) throw new IllegalArgumentException("responseAuthority must be legacy");
        if (responseModified || persistenceAuthority || exposureAuthority || releaseGateAuthority || metricAuthority
                || productionCursorAuthority || productionActivationAuthority || apiCutoverAuthority) {
            throw new IllegalArgumentException("IP-7 wiring cannot activate response mutation or production authority");
        }
    }
    public static SearchShadowWiringAuthorityV1 legacyOnly() {
        return new SearchShadowWiringAuthorityV1("legacy", false, false, false, false, false, false, false, false);
    }
}
