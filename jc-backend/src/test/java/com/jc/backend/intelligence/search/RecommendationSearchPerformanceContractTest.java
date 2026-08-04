package com.jc.backend.intelligence.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RecommendationSearchPerformanceContractTest {

    private static final Path SOURCE = Path.of(
            "src/main/java/com/jc/backend/intelligence/search/RecommendationSearchCandidateSource.java");

    @Test
    void candidateQueryUsesBoundedSetAggregationsInsteadOfPerCandidateCounts() throws Exception {
        String source = Files.readString(SOURCE);

        assertThat(source).contains(
                "eligible as (",
                "tag_data as (",
                "like_counts as (",
                "bookmark_counts as (",
                "count(*) over() as total_count",
                "limit ?");
        assertThat(source).doesNotContain(
                "(select count(*) from public.post_likes",
                "(select count(*) from public.bookmarks",
                "where pt.post_id = p.id");
    }

    @Test
    void searchRuntimeKeepsOneCandidateReadAndOneOrderedSummaryBatch() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/jc/backend/intelligence/search/RecommendationSearchService.java"));

        assertThat(count(source, "candidateSource.findEligible(")).isEqualTo(1);
        assertThat(count(source, "postService.summariesByOrderedIds(")).isEqualTo(1);
        assertThat(source).contains(
                "totalElements > rows.size()",
                "SEARCH_SNAPSHOT_EXPIRED",
                "snapshotFingerprint(ranked)");
    }

    private static int count(String value, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }
}
