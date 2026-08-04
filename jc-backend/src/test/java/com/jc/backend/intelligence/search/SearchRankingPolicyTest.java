package com.jc.backend.intelligence.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.jc.backend.intelligence.search.RecommendationSearchProfileSource.SearchInterestProfile;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SearchRankingPolicyTest {

    private final SearchRankingPolicy policy = new SearchRankingPolicy();
    private final Instant referenceTime = Instant.parse("2026-08-04T00:00:00Z");

    @Test
    void higherSearchRelevanceWinsBeforePersonalizationAndPopularity() {
        var exact = candidate(1, 1, true, false, false, false, false, 0, 0, 0, "history");
        var popularInterest = candidate(
                2, 2, false, false, true, false, false, 10_000, 5_000, 2_000, "cafe");

        var ranked = policy.rank(
                List.of(popularInterest, exact),
                new SearchInterestProfile(Map.of("theme:cafe", 1.0d)),
                referenceTime);

        assertThat(ranked).extracting(result -> result.candidate().postId())
                .containsExactly(1L, 2L);
    }

    @Test
    void matchingInterestBreaksEqualSearchRelevance() {
        var cafe = candidate(1, 1, false, false, true, false, false, 0, 0, 0, "cafe");
        var history = candidate(2, 2, false, false, true, false, false, 0, 0, 0, "history");

        var ranked = policy.rank(
                List.of(history, cafe),
                new SearchInterestProfile(Map.of("theme:cafe", 0.8d)),
                referenceTime);

        assertThat(ranked).extracting(result -> result.candidate().postId())
                .containsExactly(1L, 2L);
    }

    @Test
    void popularityAndFreshnessAreAuxiliaryWithinEqualRelevanceAndInterest() {
        var oldLow = candidate(
                1, 1, false, false, true, false, false, 0, 0, 0, "cafe",
                Instant.parse("2025-01-01T00:00:00Z"));
        var recentPopular = candidate(
                2, 2, false, false, true, false, false, 100, 20, 10, "cafe",
                Instant.parse("2026-08-03T00:00:00Z"));

        var ranked = policy.rank(
                List.of(oldLow, recentPopular),
                SearchInterestProfile.empty(),
                referenceTime);

        assertThat(ranked).extracting(result -> result.candidate().postId())
                .containsExactly(2L, 1L);
    }

    @Test
    void repeatedAuthorReceivesDeterministicDiversityAdjustment() {
        var first = candidate(3, 10, false, false, true, false, false, 100, 10, 2, "cafe");
        var sameAuthor = candidate(2, 10, false, false, true, false, false, 100, 10, 2, "cafe");
        var otherAuthor = candidate(1, 20, false, false, true, false, false, 100, 10, 2, "history");

        var ranked = policy.rank(
                List.of(first, sameAuthor, otherAuthor),
                SearchInterestProfile.empty(),
                referenceTime);

        assertThat(ranked.get(1).candidate().postId()).isEqualTo(1L);
        assertThat(ranked.get(2).diversityAdjustment()).isNegative();
    }

    @Test
    void identicalInputAndReferenceTimeProduceIdenticalOrder() {
        var candidates = List.of(
                candidate(1, 1, false, false, true, false, false, 10, 1, 0, "cafe"),
                candidate(2, 2, false, false, true, false, false, 10, 1, 0, "history"),
                candidate(3, 3, false, false, true, false, false, 10, 1, 0, "nature"));

        var first = policy.rank(candidates, SearchInterestProfile.empty(), referenceTime);
        var second = policy.rank(candidates, SearchInterestProfile.empty(), referenceTime);

        assertThat(first).extracting(result -> result.candidate().postId())
                .containsExactlyElementsOf(second.stream()
                        .map(result -> result.candidate().postId())
                        .toList());
    }

    @Test
    void stableTieBreakUsesCreatedAtThenPostIdDescending() {
        Instant createdAt = Instant.parse("2026-08-01T00:00:00Z");
        var lowerId = candidate(
                1, 1, false, false, true, false, false, 0, 0, 0, "cafe", createdAt);
        var higherId = candidate(
                2, 2, false, false, true, false, false, 0, 0, 0, "cafe", createdAt);

        var ranked = policy.rank(
                List.of(lowerId, higherId),
                SearchInterestProfile.empty(),
                referenceTime);

        assertThat(ranked).extracting(result -> result.candidate().postId())
                .containsExactly(2L, 1L);
    }

    private SearchRankingPolicy.SearchCandidate candidate(
            long postId,
            long authorId,
            boolean titleExact,
            boolean titlePrefix,
            boolean titleContains,
            boolean tagExact,
            boolean tagContains,
            long views,
            long likes,
            long bookmarks,
            String tag) {
        return candidate(
                postId,
                authorId,
                titleExact,
                titlePrefix,
                titleContains,
                tagExact,
                tagContains,
                views,
                likes,
                bookmarks,
                tag,
                Instant.parse("2026-08-01T00:00:00Z"));
    }

    private SearchRankingPolicy.SearchCandidate candidate(
            long postId,
            long authorId,
            boolean titleExact,
            boolean titlePrefix,
            boolean titleContains,
            boolean tagExact,
            boolean tagContains,
            long views,
            long likes,
            long bookmarks,
            String tag,
            Instant publishedAt) {
        return new SearchRankingPolicy.SearchCandidate(
                postId,
                authorId,
                "LU-LUX",
                "lu-lux",
                List.of("룩셈부르크", "Luxembourg"),
                titleExact ? "카페" : "여행 카페 기록",
                List.of(tag),
                titleExact,
                titlePrefix,
                titleContains,
                tagExact,
                tagContains,
                false,
                false,
                false,
                publishedAt,
                publishedAt,
                views,
                likes,
                bookmarks,
                0);
    }
}
