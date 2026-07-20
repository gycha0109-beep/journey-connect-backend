package com.jc.intelligence.integration.search.v1;

import com.jc.intelligence.compat.search.explore.v1.LegacyExploreCompatibilityResult;

@FunctionalInterface
public interface SearchShadowIntegrationPort<T> {
    SearchShadowIntegrationResult<T> integrate(
            T legacyResponse,
            LegacyExploreCompatibilityResult legacyCompatibility,
            SearchShadowContextV1 context,
            SearchShadowRuntimeInputProvider runtimeInputProvider);
}
