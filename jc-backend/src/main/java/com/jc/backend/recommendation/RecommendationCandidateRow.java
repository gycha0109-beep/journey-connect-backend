package com.jc.backend.recommendation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** canonical posts 조회 결과를 Core 독립 모델로 전달하기 전의 DB projection입니다. */
public record RecommendationCandidateRow(
        long postId,
        long authorId,
        String regionSlug,
        String visibility,
        Instant createdAt,
        Instant publishedAt,
        long viewCount,
        long likeCount,
        long bookmarkCount,
        int recentExposureCount,
        List<String> tagSlugs) {

    public RecommendationCandidateRow {
        if (postId <= 0 || authorId <= 0) {
            throw new IllegalArgumentException("candidate post and author IDs must be positive");
        }
        if (viewCount < 0 || likeCount < 0 || bookmarkCount < 0 || recentExposureCount < 0) {
            throw new IllegalArgumentException("candidate counters must be nonnegative");
        }
        Objects.requireNonNull(regionSlug, "regionSlug");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(publishedAt, "publishedAt");
        tagSlugs = List.copyOf(Objects.requireNonNull(tagSlugs, "tagSlugs"));
    }
}
