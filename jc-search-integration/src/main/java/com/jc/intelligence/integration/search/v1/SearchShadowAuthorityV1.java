package com.jc.intelligence.integration.search.v1;

public record SearchShadowAuthorityV1(
        String responseAuthority,
        boolean responseModified,
        boolean persistenceAuthority,
        boolean exposureAuthority,
        boolean releaseGateAuthority,
        boolean metricAuthority,
        boolean productionCursorAuthority,
        boolean apiCutoverAuthority) {
    public SearchShadowAuthorityV1 {
        if (!"legacy".equals(responseAuthority)) {
            throw new IllegalArgumentException("responseAuthority must be legacy");
        }
        if (responseModified || persistenceAuthority || exposureAuthority || releaseGateAuthority
                || metricAuthority || productionCursorAuthority || apiCutoverAuthority) {
            throw new IllegalArgumentException("IP-6 cannot activate response mutation or production authorities");
        }
    }

    public static SearchShadowAuthorityV1 legacyOnly() {
        return new SearchShadowAuthorityV1("legacy", false, false, false, false, false, false, false);
    }
}
