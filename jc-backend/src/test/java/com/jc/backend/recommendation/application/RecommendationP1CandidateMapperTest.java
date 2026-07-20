package com.jc.backend.recommendation.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.jc.backend.recommendation.RecommendationCandidateRow;
import com.jc.backend.recommendation.RecommendationCoreInputMapper;
import com.jc.backend.recommendation.p1.RecommendationP1CandidateMapper;
import com.jc.recommendation.p1.ranking.P1CandidateInput;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("p1-verification")
class RecommendationP1CandidateMapperTest {

    private final RecommendationP1CandidateMapper mapper =
            new RecommendationP1CandidateMapper(new RecommendationCoreInputMapper());

    @Test
    void mapsV1AndP1FeatureVocabularyWithoutChangingTheV1Mapper() {
        Instant publishedAt = Instant.parse("2026-07-19T00:00:00Z");
        RecommendationCandidateRow row = new RecommendationCandidateRow(
                41L,
                7L,
                "kr-seoul",
                "public",
                publishedAt.minusSeconds(60),
                publishedAt,
                10L,
                2L,
                1L,
                0,
                List.of("food", "history", "running", "unknown-tag"));

        P1CandidateInput candidate = mapper.map(row);

        assertThat(candidate.featureIds())
                .contains("region:seoul", "theme:food", "theme:history", "activity:running")
                .doesNotContain("unknown-tag");
        assertThat(candidate.diversityMetadata().primaryThemeFeatureId()).isEqualTo("theme:food");
        assertThat(candidate.entityId()).isEqualTo("41");
    }
}
