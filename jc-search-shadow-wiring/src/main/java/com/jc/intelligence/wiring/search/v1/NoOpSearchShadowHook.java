package com.jc.intelligence.wiring.search.v1;

public final class NoOpSearchShadowHook<T> implements SearchShadowHook<T> {
    @Override public SearchShadowDispatchReceiptV1<T> dispatch(SearchShadowHookRequestV1<T> request) {
        if (request == null) throw new IllegalArgumentException("request is required");
        return new SearchShadowDispatchReceiptV1<>(request.legacyResponse(), SearchShadowDispatchStatus.DISABLED,
                null, null, null, null, "no_op_disabled", SearchShadowWiringAuthorityV1.legacyOnly());
    }
}
