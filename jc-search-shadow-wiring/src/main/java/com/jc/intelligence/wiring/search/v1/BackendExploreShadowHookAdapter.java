package com.jc.intelligence.wiring.search.v1;

import com.jc.intelligence.compat.search.explore.v1.LegacyExploreCompatibilityAdapter;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreCompatibilityResult;
import java.util.Objects;

public final class BackendExploreShadowHookAdapter<T> implements SearchShadowHook<T> {
    private final LegacyExploreCompatibilityAdapter compatibilityAdapter;
    private final SearchShadowDispatcher<T> dispatcher;
    public BackendExploreShadowHookAdapter(
            LegacyExploreCompatibilityAdapter compatibilityAdapter, SearchShadowDispatcher<T> dispatcher) {
        this.compatibilityAdapter = Objects.requireNonNull(compatibilityAdapter, "compatibilityAdapter");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
    }
    @Override public SearchShadowDispatchReceiptV1<T> dispatch(SearchShadowHookRequestV1<T> request) {
        Objects.requireNonNull(request, "request");
        LegacyExploreCompatibilityResult compatibility = compatibilityAdapter.adapt(
                request.legacyRequest(), request.legacyPage(), request.compatibilityContext());
        return dispatcher.dispatch(request.legacyResponse(), compatibility, request.shadowContext());
    }
}
