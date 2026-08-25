package com.jc.backend.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jc.backend.CanonicalPostgresTest;
import com.jc.backend.recommendation.persistence.CrewRecommendationExposureStore;
import com.jc.backend.recommendation.persistence.CrewRecommendationExposureStore.CandidateWrite;
import com.jc.backend.recommendation.persistence.CrewRecommendationExposureStore.ExposureWrite;
import com.jc.backend.recommendation.persistence.CrewRecommendationExposureStore.StoreResult;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

@CanonicalPostgresTest
class CrewRecommendationExposureStoreIntegrationTest {

    @Autowired private CrewRecommendationExposureStore store;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void storesExactCandidateSetAndClassifiesExactDuplicate() {
        ExposureWrite write = exposure(
                "crew-exposure-integration-1",
                bytes("event:integration:1"),
                List.of(
                        candidate(1, 101L, 0.75d, "full_featured", "candidate:1"),
                        candidate(2, 202L, 0.50d, "legacy_tagless", "candidate:2")));

        assertThat(store.store(write)).isEqualTo(StoreResult.STORED);
        assertThat(store.store(write)).isEqualTo(StoreResult.DUPLICATE);

        Integer eventCount = jdbcTemplate.queryForObject(
                """
                select count(*)
                from public.crew_recommendation_exposure_event
                where exposure_id = ?
                """,
                Integer.class,
                write.exposureId());
        List<Long> crewIds = jdbcTemplate.queryForList(
                """
                select crew_id
                from public.crew_recommendation_exposure_candidate
                where exposure_id = ?
                order by absolute_rank
                """,
                Long.class,
                write.exposureId());

        assertThat(eventCount).isEqualTo(1);
        assertThat(crewIds).containsExactly(101L, 202L);
    }

    @Test
    void sameExposureIdWithDifferentCanonicalContentIsConflict() {
        ExposureWrite original = exposure(
                "crew-exposure-integration-2",
                bytes("event:integration:2"),
                List.of(candidate(1, 303L, 0.25d, "full_featured", "candidate:3")));
        ExposureWrite conflicting = exposure(
                "crew-exposure-integration-2",
                bytes("event:integration:2:changed"),
                List.of(candidate(1, 303L, 0.25d, "full_featured", "candidate:3")));

        assertThat(store.store(original)).isEqualTo(StoreResult.STORED);
        assertThatThrownBy(() -> store.store(conflicting))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already bound to different content");
    }

    @Test
    void appendOnlyTriggerRejectsMutation() {
        ExposureWrite write = exposure(
                "crew-exposure-integration-3",
                bytes("event:integration:3"),
                List.of(candidate(1, 404L, 0.10d, "legacy_tagless", "candidate:4")));
        assertThat(store.store(write)).isEqualTo(StoreResult.STORED);

        assertThatThrownBy(() -> jdbcTemplate.update(
                        """
                        update public.crew_recommendation_exposure_event
                        set requested_limit = 2
                        where exposure_id = ?
                        """,
                        write.exposureId()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    private static ExposureWrite exposure(
            String exposureId,
            byte[] canonicalPayload,
            List<CandidateWrite> candidates) {
        return new ExposureWrite(
                exposureId,
                "crew_recommendation_exposure_v1",
                "server_delivery_commit_v1",
                77L,
                "crew_list",
                Instant.parse("2026-08-25T06:30:00.123456Z"),
                Instant.parse("2026-08-25T06:29:59.654321Z"),
                "crew-recommendation-contract-v1",
                "crew-ranking-policy-v1",
                "crew-score-policy-v1",
                "crew-profile-policy-v1",
                "crew-feature-vocabulary-v1",
                "a".repeat(64),
                5,
                canonicalPayload,
                candidates);
    }

    private static CandidateWrite candidate(
            int rank,
            long crewId,
            double score,
            String coverageMode,
            String evidence) {
        return new CandidateWrite(rank, crewId, score, coverageMode, bytes(evidence));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
