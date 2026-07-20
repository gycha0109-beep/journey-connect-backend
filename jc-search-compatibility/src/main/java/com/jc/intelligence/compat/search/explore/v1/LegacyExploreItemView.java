package com.jc.intelligence.compat.search.explore.v1;

import java.time.Instant;

public record LegacyExploreItemView(
        Long id, String title, String regionCode, String regionName, String coverImageUrl,
        Long viewCount, Long likeCount, Long bookmarkCount, LegacyExploreAuthorView author, Instant createdAt) { }
