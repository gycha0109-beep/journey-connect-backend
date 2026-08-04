package com.jc.backend.intelligence.search;

import com.jc.backend.common.DomainException;
import com.jc.backend.common.PageResponse;
import com.jc.backend.intelligence.search.RecommendationSearchProfileSource.SearchInterestProfile;
import com.jc.backend.post.PostDtos;
import com.jc.backend.post.PostService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
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
    private final SearchContextCodec contextCodec;
    private final boolean enabled;
    private final int candidateLimit;

    public RecommendationSearchService(
            PostService postService,
            RecommendationSearchCandidateSource candidateSource,
            RecommendationSearchCandidateMapper candidateMapper,
            RecommendationSearchProfileSource profileSource,
            SearchRankingPolicy rankingPolicy,
            SearchContextCodec contextCodec,
            @Value("${app.recommendation.search.enabled:false}") boolean enabled,
            @Value("${app.recommendation.search.candidate-limit:1000}") int candidateLimit) {
        this.postService = postService;
        this.candidateSource = candidateSource;
        this.candidateMapper = candidateMapper;
        this.profileSource = profileSource;
        this.rankingPolicy = rankingPolicy;
        this.contextCodec = contextCodec;
        this.enabled = enabled;
        this.candidateLimit = Math.min(Math.max(candidateLimit, 1), 5_000);
    }

    public PageResponse<PostDtos.Summary> explore(
            String keyword,
            String region,
            Pageable pageable,
            Long userId,
            PageResponse<PostDtos.Summary> legacyResponse) {
        return exploreWithContext(
                keyword,
                region,
                pageable,
                userId,
                null,
                legacyResponse).page();
    }

    public SearchExploreResult exploreWithContext(
            String keyword,
            String region,
            Pageable pageable,
            Long userId,
            String snapshotToken,
            PageResponse<PostDtos.Summary> legacyResponse) {
        if (!shouldRank(keyword, region, pageable, userId)) {
            return SearchExploreResult.legacy(legacyResponse);
        }
        boolean continuation = hasText(snapshotToken);
        if (pageable.getPageNumber() > 0 && !continuation) {
            return SearchExploreResult.legacy(legacyResponse);
        }

        try {
            Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            String queryFingerprint = queryFingerprint(
                    userId,
                    keyword,
                    region,
                    pageable.getPageSize());
            SearchContextCodec.SnapshotContext supplied = continuation
                    ? contextCodec.decodeSnapshot(
                            snapshotToken,
                            userId,
                            queryFingerprint,
                            pageable.getPageSize(),
                            now)
                    : null;
            Instant referenceTime = supplied == null ? now : supplied.referenceTime();
            SearchInterestProfile profile = profileSource.find(userId, referenceTime);
            List<RecommendationSearchCandidateRow> rows = candidateSource.findEligible(
                    userId,
                    keyword,
                    region,
                    candidateLimit,
                    referenceTime);
            if (rows.isEmpty()) {
                if (continuation) {
                    throw snapshotExpired();
                }
                return new SearchExploreResult(empty(pageable), null, null, null, null);
            }

            long totalElements = rows.get(0).totalCount();
            if (totalElements > rows.size()) {
                log.info(
                        "Search recommendation candidate pool incomplete; using legacy page"
                                + " [total={}, loaded={}]",
                        totalElements,
                        rows.size());
                if (continuation) {
                    throw snapshotExpired();
                }
                return SearchExploreResult.legacy(legacyResponse);
            }

            List<SearchRankingPolicy.RankedSearchCandidate> ranked = rankingPolicy.rank(
                    candidateMapper.mapAll(rows),
                    profile,
                    referenceTime);
            String snapshotFingerprint = snapshotFingerprint(ranked);
            SearchContextCodec.SnapshotContext snapshot;
            String resolvedSnapshotToken;
            if (supplied != null) {
                if (!supplied.policyVersion().equals(SearchRankingPolicy.POLICY_VERSION)
                        || !supplied.snapshotFingerprint().equals(snapshotFingerprint)) {
                    throw snapshotExpired();
                }
                snapshot = supplied;
                resolvedSnapshotToken = snapshotToken;
            } else {
                String runId = searchRunId(
                        userId,
                        queryFingerprint,
                        referenceTime,
                        snapshotFingerprint);
                resolvedSnapshotToken = contextCodec.encodeSnapshot(
                        runId,
                        userId,
                        queryFingerprint,
                        referenceTime,
                        pageable.getPageSize(),
                        snapshotFingerprint,
                        SearchRankingPolicy.POLICY_VERSION,
                        now);
                snapshot = contextCodec.decodeSnapshot(
                        resolvedSnapshotToken,
                        userId,
                        queryFingerprint,
                        pageable.getPageSize(),
                        now);
            }

            long offset = pageable.getOffset();
            int from = Math.toIntExact(Math.min(offset, ranked.size()));
            int to = Math.min(from + pageable.getPageSize(), ranked.size());
            List<SearchRankingPolicy.RankedSearchCandidate> pageCandidates =
                    ranked.subList(from, to);
            List<Long> orderedIds = pageCandidates.stream()
                    .map(result -> result.candidate().postId())
                    .toList();
            List<PostDtos.Summary> summaries = orderedIds.isEmpty()
                    ? List.of()
                    : postService.summariesByOrderedIds(orderedIds);
            if (summaries.size() != orderedIds.size()
                    || !summaries.stream().map(PostDtos.Summary::id).toList().equals(orderedIds)) {
                log.info("Search recommendation visibility changed during delivery");
                if (continuation) {
                    throw snapshotExpired();
                }
                return SearchExploreResult.legacy(legacyResponse);
            }
            List<SearchContextCodec.ResultBinding> bindings = pageCandidates.stream()
                    .map(candidate -> new SearchContextCodec.ResultBinding(
                            candidate.candidate().postId(),
                            candidate.rank()))
                    .toList();
            String resultContext = contextCodec.encodeResultContext(snapshot, bindings, now);
            return new SearchExploreResult(
                    page(summaries, pageable, totalElements),
                    resolvedSnapshotToken,
                    snapshot.runId(),
                    snapshot.policyVersion(),
                    resultContext);
        } catch (DomainException exception) {
            if (continuation) {
                throw exception;
            }
            log.warn("Search recommendation first page failed open: {}", exception.code());
            return SearchExploreResult.legacy(legacyResponse);
        } catch (RuntimeException exception) {
            if (continuation) {
                log.warn("Search recommendation continuation failed closed: {}",
                        exception.getClass().getSimpleName());
                throw snapshotExpired();
            }
            log.warn(
                    "Search recommendation failed open: {}",
                    exception.getClass().getSimpleName());
            return SearchExploreResult.legacy(legacyResponse);
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

    private String queryFingerprint(
            long userId,
            String keyword,
            String region,
            int pageSize) {
        return SearchHashing.sha256(String.join("|",
                Long.toString(userId),
                normalize(keyword),
                normalize(region),
                Integer.toString(pageSize),
                SearchRankingPolicy.POLICY_VERSION));
    }

    private String snapshotFingerprint(
            List<SearchRankingPolicy.RankedSearchCandidate> ranked) {
        StringBuilder material = new StringBuilder(SearchRankingPolicy.POLICY_VERSION);
        for (SearchRankingPolicy.RankedSearchCandidate candidate : ranked) {
            material.append('|').append(candidate.rank())
                    .append(':').append(candidate.candidate().postId())
                    .append(':').append(candidate.searchRelevance())
                    .append(':').append(Double.toHexString(candidate.interestMatch()))
                    .append(':').append(Double.toHexString(candidate.popularity()))
                    .append(':').append(Double.toHexString(candidate.freshness()))
                    .append(':').append(Double.toHexString(candidate.repeatExposurePenalty()))
                    .append(':').append(Double.toHexString(candidate.diversityAdjustment()))
                    .append(':').append(Double.toHexString(candidate.auxiliaryScore()))
                    .append(':').append(candidate.candidate().createdAt().toEpochMilli());
        }
        return SearchHashing.sha256(material.toString());
    }

    private String searchRunId(
            long userId,
            String queryFingerprint,
            Instant referenceTime,
            String snapshotFingerprint) {
        String material = userId + "|" + queryFingerprint + "|"
                + referenceTime.toEpochMilli() + "|" + snapshotFingerprint;
        return "search:" + SearchHashing.sha256(material).substring(0, 48);
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

    private static String normalize(String value) {
        return value == null || value.isBlank()
                ? ""
                : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static DomainException snapshotExpired() {
        return new DomainException(
                HttpStatus.CONFLICT,
                "SEARCH_SNAPSHOT_EXPIRED",
                "탐색 결과가 변경되었습니다. 첫 페이지부터 다시 요청해 주세요.");
    }

    public record SearchExploreResult(
            PageResponse<PostDtos.Summary> page,
            String snapshotToken,
            String runId,
            String policyVersion,
            String resultContextToken) {

        public static SearchExploreResult legacy(PageResponse<PostDtos.Summary> page) {
            return new SearchExploreResult(page, null, null, null, null);
        }
    }
}
