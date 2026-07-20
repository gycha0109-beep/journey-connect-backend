package com.jc.backend.search.shadow;

import com.jc.backend.common.PageResponse;
import com.jc.backend.post.PostDtos;
import com.jc.intelligence.wiring.search.v1.SearchShadowHookRequestV1;
import org.springframework.data.domain.Pageable;

/** Converts backend explore values to the approved IP-4/IP-7 public compatibility contracts. */
@FunctionalInterface
public interface ExploreShadowHookRequestFactory {
    SearchShadowHookRequestV1<PageResponse<PostDtos.Summary>> create(
            String keyword,
            String region,
            Pageable pageable,
            PageResponse<PostDtos.Summary> legacyResponse);
}
