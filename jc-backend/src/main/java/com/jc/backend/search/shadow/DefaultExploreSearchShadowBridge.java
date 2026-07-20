package com.jc.backend.search.shadow;

import com.jc.backend.common.PageResponse;
import com.jc.backend.post.PostDtos;
import com.jc.intelligence.wiring.search.v1.SearchShadowHook;
import com.jc.intelligence.wiring.search.v1.SearchShadowHookRequestV1;
import java.util.Objects;
import org.springframework.data.domain.Pageable;

/**
 * Explicitly assembled controlled bridge. This class is not a Spring component and is never selected by default.
 * Ordinary runtime failures are isolated inside the bridge; fatal JVM errors are not swallowed.
 */
public final class DefaultExploreSearchShadowBridge implements ExploreSearchShadowBridge {
    private final ExploreShadowHookRequestFactory requestFactory;
    private final SearchShadowHook<PageResponse<PostDtos.Summary>> shadowHook;

    public DefaultExploreSearchShadowBridge(
            ExploreShadowHookRequestFactory requestFactory,
            SearchShadowHook<PageResponse<PostDtos.Summary>> shadowHook) {
        this.requestFactory = Objects.requireNonNull(requestFactory, "requestFactory");
        this.shadowHook = Objects.requireNonNull(shadowHook, "shadowHook");
    }

    @Override
    public void afterExplore(
            String keyword,
            String region,
            Pageable pageable,
            PageResponse<PostDtos.Summary> legacyResponse) {
        try {
            SearchShadowHookRequestV1<PageResponse<PostDtos.Summary>> request =
                    requestFactory.create(keyword, region, pageable, legacyResponse);
            shadowHook.dispatch(request);
        } catch (RuntimeException ignored) {
            // Shadow conversion/dispatch is fail-open for the legacy response. Fatal JVM errors are not swallowed.
        }
    }
}
