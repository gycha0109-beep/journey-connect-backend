package com.jc.intelligence.wiring.search.v1;

@FunctionalInterface
public interface SearchShadowHook<T> {
    SearchShadowDispatchReceiptV1<T> dispatch(SearchShadowHookRequestV1<T> request);
}
