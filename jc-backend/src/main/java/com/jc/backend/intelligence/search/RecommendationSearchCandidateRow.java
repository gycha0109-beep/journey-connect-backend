package com.jc.backend.intelligence.search;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record RecommendationSearchCandidateRow(
        long postId,
        long authorId,
        String regionCode,
        String regionSlug,
        List<String> regionNames,
        String title,
        List<String> tagSlugs,
        boolean titleExactMatch,
        boolean titlePrefixMatch,
        boolean titleContainsMatch,
        boolean tagExactMatch,
        boolean tagContainsMatch,
        boolean regionExactMatch,
        boolean regionContainsMatch,
        boolean contentMatch,
        Instant createdAt,
        Instant publishedAt,
        long viewCount,
        long likeCount,
        long bookmarkCount,
        int recentExposureCount,
        long totalCount) {

    public RecommendationSearchCandidateRow {
        if (postId <= 0 || authorId <= 0) {
            throw new IllegalArgumentException("search candidate IDs must be positive");
        }
        Objects.requireNonNull(regionCode, "regionCode");
        Objects.requireNonNull(regionSlug, "regionSlug");
        regionNames = List.copyOf(Objects.requireNonNull(regionNames, "regionNames"));
        Objects.requireNonNull(title, "title");
        tagSlugs = List.copyOf(Objects.requireNonNull(tagSlugs, "tagSlugs"));
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(publishedAt, "publishedAt");
        if (viewCount < 0 || likeCount < 0 || bookmarkCount < 0
                || recentExposureCount < 0 || totalCount < 0) {
            throw new IllegalArgumentException("search candidate counters must be nonnegative");
        }
    }
}
