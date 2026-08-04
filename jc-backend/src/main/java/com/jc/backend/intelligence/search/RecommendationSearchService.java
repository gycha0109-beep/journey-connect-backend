package com.jc.backend.intelligence.search;

import com.jc.backend.common.PageResponse;
import com.jc.backend.intelligence.search.RecommendationSearchProfileSource.SearchInterestProfile;
import com.jc.backend.post.PostDtos;
import com.jc.backend.post.PostService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class RecommendationSearchService {

    private static final Logger log =
            LoggerFactory.getLogger(RecommendationSearchService.class);

    private final PostService postService;
    private final RecommendationSearchCandidateSource candidateSource;
    private final RecommendationSearchCandidateMapper candidateMapper;
    private final RecommendationSearchProfileSource profileSource;
    private final SearchRankingPolicy rankingPolicy;
    private final boolean enabled;
    private final int candidateLimit;

    public RecommendationSearchService(
            PostService postService,
            RecommendationSearchCandidateSource candidateSource,
            RecommendationSearchCandidateMapper candidateMapper,
            RecommendationSearchProfileSource profileSource,
            SearchRankingPolicy rankingPolicy,
            @Value("${app.recommendation.search.enabled:false}") boolean enabled,
            @Value("${app.recommendation.search.candidate-limit:1000}") int candidateLimit) {
        this.postService = postService;
        this.candidateSource = candidateSource;
        this.candidateMapper = candidateMapper;
        this.profileSource = profileSource;
        this.rankingPolicy = rankingPolicy;
        this.enabled = enabled;
        this.candidateLimit = Math.min(Math.max(candidateLimit, 1), 5_000);
    }

    public PageResponse<PostDtos.Summary> explore(
            String keyword,
            String region,
            Pageable pageable,
            Long userId,
            PageResponse<PostDtos.Summary> legacyResponse) {
        if (!shouldRank(keyword, region, pageable, userId)) {
            return legacyResponse;
        }

        try {
            Instant referenceTime = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            SearchInterestProfile profile = profileSource.find(userId, referenceTime);
            List<RecommendationSearchCandidateRow> rows = candidateSource.findEligible(
                    userId,
                    keyword,
                    region,
                    candidateLimit,
                    referenceTime);
            if (rows.isEmpty()) {
                return empty(pageable);
            }

            long totalElements = rows.get(0).totalCount();
            if (totalElements > rows.size()) {
                log.info(
                        "Search recommendation candidate pool incomplete; using legacy page"
                                + " [total={}, loaded={}]",
                        totalElements,
                        rows.size());
                return legacyResponse;
            }

            List<SearchRankingPolicy.RankedSearchCandidate> ranked = rankingPolicy.rank(
                    candidateMapper.mapAll(rows),
                    profile,
                    referenceTime);
            long offset = pageable.getOffset();
            if (offset >= ranked.size()) {
                return page(List.of(), pageable, totalElements);
            }
            int from = Math.toIntExact(offset);
            int to = Math.min(from + pageable.getPageSize(), ranked.size());
            List<Long> orderedIds = ranked.subList(from, to).stream()
                    .map(result -> result.candidate().postId())
                    .toList();
            List<PostDtos.Summary> summaries = postService.summariesByOrderedIds(orderedIds);
            if (summaries.size() != orderedIds.size()) {
                log.info("Search recommendation visibility changed; using legacy page");
                return legacyResponse;
            }
            return page(summaries, pageable, totalElements);
        } catch (RuntimeException exception) {
            log.warn(
                    "Search recommendation failed open: {}",
                    exception.getClass().getSimpleName());
            return legacyResponse;
        }
    }

    private boolean shouldRank(
            String keyword,
            String region,
            Pageable pageable,
            Long userId) {
        if (!enabled || userId == null || userId <= 0) {
            return false;
        }
        if (!hasText(keyword) && !hasText(region)) {
            return false;
        }
        return pageable.isPaged()
                && pageable.getPageSize() >= 1
                && pageable.getPageSize() <= 100
                && pageable.getSort().isUnsorted();
    }

    private PageResponse<PostDtos.Summary> empty(Pageable pageable) {
        return new PageResponse<>(
                List.of(),
                pageable.getPageNumber(),
                pageable.getPageSize(),
                0,
                0,
                true);
    }

    private PageResponse<PostDtos.Summary> page(
            List<PostDtos.Summary> items,
            Pageable pageable,
            long totalElements) {
        int size = pageable.getPageSize();
        int totalPages = totalElements == 0
                ? 0
                : Math.toIntExact((totalElements + size - 1) / size);
        boolean last = pageable.getOffset() + items.size() >= totalElements;
        return new PageResponse<>(
                items,
                pageable.getPageNumber(),
                size,
                totalElements,
                totalPages,
                last);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
