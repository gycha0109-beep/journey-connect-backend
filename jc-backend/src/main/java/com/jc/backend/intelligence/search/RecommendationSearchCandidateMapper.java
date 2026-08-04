package com.jc.backend.intelligence.search;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RecommendationSearchCandidateMapper {

    public List<SearchRankingPolicy.SearchCandidate> mapAll(
            List<RecommendationSearchCandidateRow> rows) {
        return rows.stream().map(this::map).toList();
    }

    private SearchRankingPolicy.SearchCandidate map(
            RecommendationSearchCandidateRow row) {
        return new SearchRankingPolicy.SearchCandidate(
                row.postId(),
                row.authorId(),
                row.regionCode(),
                row.regionSlug(),
                row.regionNames(),
                row.title(),
                row.tagSlugs(),
                row.titleExactMatch(),
                row.titlePrefixMatch(),
                row.titleContainsMatch(),
                row.tagExactMatch(),
                row.tagContainsMatch(),
                row.regionExactMatch(),
                row.regionContainsMatch(),
                row.contentMatch(),
                row.createdAt(),
                row.publishedAt(),
                row.viewCount(),
                row.likeCount(),
                row.bookmarkCount(),
                row.recentExposureCount());
    }
}
