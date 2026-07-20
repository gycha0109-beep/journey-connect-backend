package com.jc.intelligence.wiring.search.v1;

import com.jc.intelligence.compat.search.explore.v1.LegacyExploreCompatibilityResult;
import com.jc.intelligence.integration.search.v1.SearchShadowContextV1;

public interface SearchShadowDispatcher<T> {
    SearchShadowDispatchReceiptV1<T> dispatch(
            T legacyResponse, LegacyExploreCompatibilityResult legacyCompatibility, SearchShadowContextV1 context);
}
