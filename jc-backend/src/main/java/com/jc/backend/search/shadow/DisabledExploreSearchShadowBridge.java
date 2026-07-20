package com.jc.backend.search.shadow;

import com.jc.backend.common.PageResponse;
import com.jc.backend.post.PostDtos;
import org.springframework.data.domain.Pageable;

/** Default production-equivalent bridge. It performs no conversion, dispatch, execution, or logging. */
public final class DisabledExploreSearchShadowBridge implements ExploreSearchShadowBridge {
    @Override
    public void afterExplore(
            String keyword,
            String region,
            Pageable pageable,
            PageResponse<PostDtos.Summary> legacyResponse) {
        // Intentionally disabled. The legacy response remains the only response authority.
    }
}
