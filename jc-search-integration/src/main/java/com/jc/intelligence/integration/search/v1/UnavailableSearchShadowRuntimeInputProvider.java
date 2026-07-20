package com.jc.intelligence.integration.search.v1;

import java.util.Objects;

public final class UnavailableSearchShadowRuntimeInputProvider implements SearchShadowRuntimeInputProvider {
    private final String safeReason;

    public UnavailableSearchShadowRuntimeInputProvider(String safeReason) {
        this.safeReason = Objects.requireNonNull(safeReason, "safeReason");
        SearchShadowRuntimeInputResultV1.unavailable(safeReason);
    }

    @Override public SearchShadowRuntimeInputResultV1 provide(SearchShadowRuntimeInputContextV1 context) {
        Objects.requireNonNull(context, "context");
        return SearchShadowRuntimeInputResultV1.unavailable(safeReason);
    }
}
