package com.jc.intelligence.integration.search.v1;

@FunctionalInterface
public interface SearchShadowRuntimeInputProvider {
    SearchShadowRuntimeInputResultV1 provide(SearchShadowRuntimeInputContextV1 context);
}
