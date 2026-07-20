package com.jc.intelligence.integration.search.v1.fixture;

import com.jc.intelligence.integration.search.v1.SearchShadowRuntimeInputContextV1;
import com.jc.intelligence.integration.search.v1.SearchShadowRuntimeInputProvider;
import com.jc.intelligence.integration.search.v1.SearchShadowRuntimeInputResultV1;
import java.util.Objects;

public final class FixtureSearchShadowRuntimeInputProvider implements SearchShadowRuntimeInputProvider {
    private final SearchShadowRuntimeInputResultV1 result;

    public FixtureSearchShadowRuntimeInputProvider(SearchShadowRuntimeInputResultV1 result) {
        this.result = Objects.requireNonNull(result, "result");
    }

    @Override public SearchShadowRuntimeInputResultV1 provide(SearchShadowRuntimeInputContextV1 context) {
        Objects.requireNonNull(context, "context");
        return result;
    }
}
