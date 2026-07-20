package com.jc.intelligence.readiness.search.v1;

public record SearchShadowReadinessAuthorityV1(
        String responseAuthority,
        boolean responseModified,
        boolean productionHookAuthority,
        boolean productionActivationAuthority,
        boolean persistenceAuthority,
        boolean exposureAuthority,
        boolean releaseGateAuthority,
        boolean metricAuthority,
        boolean productionCursorAuthority,
        boolean apiCutoverAuthority) {
    public SearchShadowReadinessAuthorityV1 {
        if (!"legacy".equals(responseAuthority)) throw new IllegalArgumentException("responseAuthority must be legacy");
        if (responseModified || productionHookAuthority || productionActivationAuthority || persistenceAuthority
                || exposureAuthority || releaseGateAuthority || metricAuthority || productionCursorAuthority
                || apiCutoverAuthority) {
            throw new IllegalArgumentException("IP-8 readiness cannot activate production authority");
        }
    }
    public static SearchShadowReadinessAuthorityV1 legacyOnly() {
        return new SearchShadowReadinessAuthorityV1("legacy", false, false, false, false, false, false, false, false, false);
    }
}
