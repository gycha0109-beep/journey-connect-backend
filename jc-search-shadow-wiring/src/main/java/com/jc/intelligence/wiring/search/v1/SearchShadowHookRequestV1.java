package com.jc.intelligence.wiring.search.v1;

import com.jc.intelligence.compat.search.explore.v1.LegacyExploreCompatibilityContext;
import com.jc.intelligence.compat.search.explore.v1.LegacyExplorePageView;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreRequestView;
import com.jc.intelligence.integration.search.v1.SearchShadowContextV1;

public record SearchShadowHookRequestV1<T>(
        T legacyResponse,
        LegacyExploreRequestView legacyRequest,
        LegacyExplorePageView legacyPage,
        LegacyExploreCompatibilityContext compatibilityContext,
        SearchShadowContextV1 shadowContext) {
    public SearchShadowHookRequestV1 {
        if (legacyResponse == null || legacyRequest == null || legacyPage == null
                || compatibilityContext == null || shadowContext == null) {
            throw new IllegalArgumentException("hook request fields are required");
        }
    }
}
