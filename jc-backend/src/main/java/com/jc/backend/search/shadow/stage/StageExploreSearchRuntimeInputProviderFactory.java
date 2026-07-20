package com.jc.backend.search.shadow.stage;

import com.jc.intelligence.compat.search.explore.v1.LegacyExploreRequestView;
import com.jc.intelligence.integration.search.v1.SearchShadowContextV1;
import com.jc.intelligence.integration.search.v1.SearchShadowRuntimeInputProvider;

@FunctionalInterface
public interface StageExploreSearchRuntimeInputProviderFactory {
    SearchShadowRuntimeInputProvider create(LegacyExploreRequestView legacyRequest, SearchShadowContextV1 shadowContext);
}
