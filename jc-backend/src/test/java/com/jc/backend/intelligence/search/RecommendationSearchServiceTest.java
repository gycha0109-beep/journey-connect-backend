package com.jc.backend.intelligence.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jc.backend.common.DomainException;
import com.jc.backend.common.PageResponse;
import com.jc.backend.intelligence.search.RecommendationSearchProfileSource.SearchInterestProfile;
import com.jc.backend.post.PostDtos;
import com.jc.backend.post.PostService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

class RecommendationSearchServiceTest {

    private static final String SECRET =
            "search-service-test-secret-with-at-least-thirty-two-bytes";
    private final PostService postService = mock(PostService.class);
    private final RecommendationSearchCandidateSource candidateSource =
            mock(RecommendationSearchCandidateSource.class);
    private final RecommendationSearchCandidateMapper candidateMapper =
            new RecommendationSearchCandidateMapper();
    private final RecommendationSearchProfileSource profileSource =
            mock(RecommendationSearchProfileSource.class);
    private final SearchRankingPolicy rankingPolicy = new SearchRankingPolicy();
    private final SearchContextCodec contextCodec = new SearchContextCodec(SECRET, 900);

    @Test
    void anonymousRequestReturnsLegacyWithoutRecommendationReads() {
        var legacy = page(9);
        var service = service(true);

        var result = service.explore(
                "카페", "룩셈부르크", PageRequest.of(0, 20), null, legacy);

        assertThat(result).isSameAs(legacy);
        verify(candidateSource, never()).findEligible(
                anyLong(), any(), any(), anyInt(), any());
    }

    @Test
    void disabledFeatureReturnsLegacy() {
        var legacy = page(9);
        var service = service(false);

        var result = service.explore(
                "카페", "룩셈부르크", PageRequest.of(0, 20), 10L, legacy);

        assertThat(result).isSameAs(legacy);
        verify(profileSource, never()).find(anyLong(), any());
    }

    @Test
    void recommendationExceptionFailsOpenToLegacy() {
        var legacy = page(9);
        var service = service(true);
        when(profileSource.find(anyLong(), any())).thenThrow(new IllegalStateException("boom"));

        var result = service.explore(
                "카페", "룩셈부르크", PageRequest.of(0, 20), 10L, legacy);

        assertThat(result).isSameAs(legacy);
    }

    @Test
    void explicitSortUsesLegacyContract() {
        var legacy = page(9);
        var service = service(true);

        var result = service.explore(
                "카페",
                "룩셈부르크",
                PageRequest.of(0, 20, Sort.by("createdAt").descending()),
                10L,
                legacy);

        assertThat(result).isSameAs(legacy);
        verify(candidateSource, never()).findEligible(
                anyLong(), any(), any(), anyInt(), any());
    }

    @Test
    void successfulRankingReturnsOrderedSummaries() {
        var legacy = page(9);
        var service = service(true);
        Instant time = Instant.parse("2026-08-01T00:00:00Z");
        var lower = row(1, false, true, 2, time);
        var higher = row(2, true, false, 2, time);
        when(profileSource.find(anyLong(), any())).thenReturn(SearchInterestProfile.empty());
        when(candidateSource.findEligible(anyLong(), anyString(), anyString(), anyInt(), any()))
                .thenReturn(List.of(lower, higher));
        var expected = List.of(summary(2), summary(1));
        when(postService.summariesByOrderedIds(List.of(2L, 1L))).thenReturn(expected);

        var result = service.exploreWithContext(
                "카페", "룩셈부르크", PageRequest.of(0, 20), 10L, null, legacy);

        assertThat(result.page().items()).containsExactlyElementsOf(expected);
        assertThat(result.page().totalElements()).isEqualTo(2);
        assertThat(result.snapshotToken()).startsWith("sc1.");
        assertThat(result.resultContextToken()).startsWith("src1.");
        assertThat(result.policyVersion()).isEqualTo(SearchRankingPolicy.POLICY_VERSION);
    }

    @Test
    void incompleteCandidatePoolFallsBackToLegacy() {
        var legacy = page(9);
        var service = service(true);
        Instant time = Instant.parse("2026-08-01T00:00:00Z");
        when(profileSource.find(anyLong(), any())).thenReturn(SearchInterestProfile.empty());
        when(candidateSource.findEligible(anyLong(), anyString(), anyString(), anyInt(), any()))
                .thenReturn(List.of(row(1, true, false, 3, time)));

        var result = service.explore(
                "카페", "룩셈부르크", PageRequest.of(0, 20), 10L, legacy);

        assertThat(result).isSameAs(legacy);
    }

    @Test
    void continuationUsesFirstPageSnapshotAndKeepsPagePartitionStable() {
        var service = service(true);
        Instant time = Instant.parse("2026-08-01T00:00:00Z");
        var rows = List.of(
                row(1, false, true, 3, time),
                row(2, true, false, 3, time),
                row(3, false, true, 3, time.minusSeconds(1)));
        when(profileSource.find(anyLong(), any())).thenReturn(SearchInterestProfile.empty());
        when(candidateSource.findEligible(anyLong(), anyString(), anyString(), anyInt(), any()))
                .thenReturn(rows);
        when(postService.summariesByOrderedIds(List.of(2L, 1L)))
                .thenReturn(List.of(summary(2), summary(1)));
        when(postService.summariesByOrderedIds(List.of(3L)))
                .thenReturn(List.of(summary(3)));

        var first = service.exploreWithContext(
                "카페", "룩셈부르크", PageRequest.of(0, 2), 10L, null, page(9));
        var second = service.exploreWithContext(
                "카페",
                "룩셈부르크",
                PageRequest.of(1, 2),
                10L,
                first.snapshotToken(),
                page(9));

        assertThat(first.page().items()).extracting(PostDtos.Summary::id)
                .containsExactly(2L, 1L);
        assertThat(second.page().items()).extracting(PostDtos.Summary::id)
                .containsExactly(3L);
        assertThat(second.snapshotToken()).isEqualTo(first.snapshotToken());
        assertThat(second.runId()).isEqualTo(first.runId());
    }

    @Test
    void changedRankingSnapshotFailsClosedOnContinuation() {
        var service = service(true);
        Instant time = Instant.parse("2026-08-01T00:00:00Z");
        var initial = List.of(
                row(1, false, true, 2, time),
                row(2, true, false, 2, time));
        var changed = List.of(
                row(1, true, false, 2, time),
                row(2, false, true, 2, time));
        when(profileSource.find(anyLong(), any())).thenReturn(SearchInterestProfile.empty());
        when(candidateSource.findEligible(anyLong(), anyString(), anyString(), anyInt(), any()))
                .thenReturn(initial, changed);
        when(postService.summariesByOrderedIds(List.of(2L)))
                .thenReturn(List.of(summary(2)));

        var first = service.exploreWithContext(
                "카페", "룩셈부르크", PageRequest.of(0, 1), 10L, null, page(9));

        assertThatThrownBy(() -> service.exploreWithContext(
                "카페",
                "룩셈부르크",
                PageRequest.of(1, 1),
                10L,
                first.snapshotToken(),
                page(9)))
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).getCode())
                .isEqualTo("SEARCH_SNAPSHOT_EXPIRED");
    }

    @Test
    void pageAfterFirstWithoutSnapshotUsesLegacyForCompatibility() {
        var legacy = page(9);
        var result = service(true).exploreWithContext(
                "카페", "룩셈부르크", PageRequest.of(1, 20), 10L, null, legacy);

        assertThat(result.page()).isSameAs(legacy);
        assertThat(result.snapshotToken()).isNull();
        verify(candidateSource, never()).findEligible(
                anyLong(), any(), any(), anyInt(), any());
    }

    private RecommendationSearchService service(boolean enabled) {
        return new RecommendationSearchService(
                postService,
                candidateSource,
                candidateMapper,
                profileSource,
                rankingPolicy,
                contextCodec,
                enabled,
                1000);
    }

    private RecommendationSearchCandidateRow row(
            long id,
            boolean exact,
            boolean contains,
            long total,
            Instant time) {
        return new RecommendationSearchCandidateRow(
                id,
                id,
                "LU-LUX",
                "lu-lux",
                List.of("룩셈부르크"),
                exact ? "카페" : "카페 산책",
                List.of("cafe"),
                exact,
                false,
                contains,
                false,
                false,
                false,
                false,
                false,
                time,
                time,
                0,
                0,
                0,
                0,
                total);
    }

    private PageResponse<PostDtos.Summary> page(long id) {
        return new PageResponse<>(List.of(summary(id)), 0, 20, 1, 1, true);
    }

    private PostDtos.Summary summary(long id) {
        return new PostDtos.Summary(
                id,
                "post-" + id,
                "LU-LUX",
                "룩셈부르크",
                null,
                0,
                0,
                0,
                new PostDtos.Author(id, "author-" + id, null),
                Instant.parse("2026-08-01T00:00:00Z"));
    }
}
