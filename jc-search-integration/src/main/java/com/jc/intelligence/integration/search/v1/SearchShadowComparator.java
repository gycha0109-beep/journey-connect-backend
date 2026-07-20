package com.jc.intelligence.integration.search.v1;

import com.jc.intelligence.compat.search.explore.v1.LegacyExploreCompatibilityResult;
import com.jc.intelligence.runtime.search.v1.SearchRuntimeResultV1;
import java.time.Duration;

@FunctionalInterface
public interface SearchShadowComparator {
    SearchShadowComparisonResultV1 compare(
            LegacyExploreCompatibilityResult legacy,
            SearchRuntimeResultV1 runtime,
            int topK,
            Duration runtimeDuration);
}
