package com.jc.backend.search.shadow;

import com.jc.backend.common.PageResponse;
import com.jc.backend.post.PostDtos;
import org.springframework.data.domain.Pageable;

/**
 * Backend-local, response-neutral boundary invoked after the legacy explore response is finalized.
 * Implementations must never acquire response authority or propagate ordinary runtime failures.
 */
@FunctionalInterface
public interface ExploreSearchShadowBridge {
    void afterExplore(
            String keyword,
            String region,
            Pageable pageable,
            PageResponse<PostDtos.Summary> legacyResponse);
}
