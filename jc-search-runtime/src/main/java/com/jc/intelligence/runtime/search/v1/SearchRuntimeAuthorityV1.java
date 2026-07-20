package com.jc.intelligence.runtime.search.v1;

public record SearchRuntimeAuthorityV1(
        String runtimeAuthority,
        boolean persistenceAuthority,
        boolean exposureAuthority,
        boolean productionCursorAuthority,
        boolean apiCutoverAuthority) {
    public SearchRuntimeAuthorityV1 {
        if (!"foundation_only".equals(runtimeAuthority)) {
            throw new IllegalArgumentException("runtimeAuthority must be foundation_only");
        }
        if (persistenceAuthority || exposureAuthority || productionCursorAuthority || apiCutoverAuthority) {
            throw new IllegalArgumentException("IP-5 cannot activate persistence, exposure, production cursor, or API authority");
        }
    }

    public static SearchRuntimeAuthorityV1 foundationOnly() {
        return new SearchRuntimeAuthorityV1("foundation_only", false, false, false, false);
    }
}
