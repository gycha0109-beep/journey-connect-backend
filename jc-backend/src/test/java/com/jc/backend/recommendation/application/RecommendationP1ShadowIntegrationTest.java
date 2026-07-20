package com.jc.backend.recommendation.application;

import static com.jc.backend.CanonicalTestData.publishedPost;
import static com.jc.backend.CanonicalTestData.region;
import static org.assertj.core.api.Assertions.assertThat;

import com.jc.backend.CanonicalPostgresTest;
import com.jc.backend.database.DatabaseRequestIdentity;
import com.jc.backend.post.JourneyPost;
import com.jc.backend.post.JourneyPostRepository;
import com.jc.backend.post.PlaceRepository;
import com.jc.backend.region.Region;
import com.jc.backend.region.RegionRepository;
import com.jc.backend.user.UserAccount;
import com.jc.backend.user.UserRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@CanonicalPostgresTest
@Tag("p1-verification")
@TestPropertySource(properties = {
        "app.recommendation.mode=SHADOW",
        "app.recommendation.p1.mode=SHADOW",
        "app.recommendation.p1.release-id=p1-shadow-integration-v1",
        "app.recommendation.p1.retrieval-limit=100",
        "app.recommendation.p1.core-candidate-limit=100",
        "app.recommendation.p1.comparison-cutoff=20"
})
class RecommendationP1ShadowIntegrationTest {

    @Autowired private UserRepository users;
    @Autowired private RegionRepository regions;
    @Autowired private JourneyPostRepository posts;
    @Autowired private PlaceRepository places;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DatabaseRequestIdentity requestIdentity;
    @Autowired private RecommendationShadowService shadowService;

    @Test
    void baselineAndTreatmentAreStoredSeparatelyWithCompleteP1Evidence() {
        UserAccount user = users.save(new UserAccount(
                "p1-shadow-user@example.com", "hash", "p1-shadow-user"));
        UserAccount other = users.save(new UserAccount(
                "p1-shadow-author@example.com", "hash", "p1-shadow-author"));
        Region seoul = region(regions, "KR-SEOUL");
        Region busan = region(regions, "KR-BUSAN");
        JourneyPost first = posts.saveAndFlush(publishedPost(
                places, other, seoul, "p1 food route", "p1 food content"));
        JourneyPost second = posts.saveAndFlush(publishedPost(
                places, other, busan, "p1 culture route", "p1 culture content"));
        tag(first, "food", "solo-travel");
        tag(second, "culture", "family-travel");
        jdbcTemplate.update(
                """
                insert into public.recommendation_user_preference
                  (user_id, feature_id, preference_kind, strength)
                values (?, 'theme:food', 'prefer', 1.0)
                """,
                user.getId());

        RecommendationShadowService.ShadowOutcome outcome;
        try (DatabaseRequestIdentity.Scope ignored = requestIdentity.open(user.getId())) {
            outcome = shadowService.observeHomeFeed(user.getId(), "jwt-p1-shadow", true);
        }

        assertThat(outcome.status()).isEqualTo(RecommendationShadowService.ShadowOutcome.Status.SUCCEEDED);
        String baselineRunId = outcome.runId();
        Map<String, Object> assignment = jdbcTemplate.queryForMap(
                """
                select baseline_run_id, treatment_run_id, user_id, session_id,
                       release_id, experiment_assignment, segment,
                       profile_snapshot_id, profile_policy_version,
                       feature_vocabulary_version, retrieval_policy_version,
                       policy_bundle_version, score_policy_version,
                       diversity_policy_version, low_exposure_policy_version,
                       exploration_policy_version, selection_reasons::text
                from public.recommendation_p1_policy_assignment
                where baseline_run_id = ?
                """,
                baselineRunId);
        String treatmentRunId = (String) assignment.get("treatment_run_id");
        assertThat(treatmentRunId).isNotEqualTo(baselineRunId);
        assertThat(assignment.get("release_id")).isEqualTo("p1-shadow-integration-v1");
        assertThat(assignment.get("experiment_assignment")).isEqualTo("treatment");
        assertThat(assignment.get("segment")).isEqualTo("explicit_only");
        assertThat(assignment.get("profile_policy_version")).isEqualTo("behavior-profile-policy-v1");
        assertThat(assignment.get("feature_vocabulary_version")).isEqualTo("feature-vocabulary-v2");
        assertThat(assignment.get("retrieval_policy_version")).isEqualTo("retrieval-policy-v2");
        assertThat(assignment.get("policy_bundle_version")).asString().contains("p1-policy-bundle");
        assertThat(assignment.get("score_policy_version")).isEqualTo("ranking-policy-v2-explicit");
        assertThat(assignment.get("selection_reasons").toString()).contains("segment:explicit_only");

        List<Map<String, Object>> runs = jdbcTemplate.queryForList(
                """
                select run_id, run_mode, run_status, user_id, session_id, context_id,
                       reference_time, ranking_policy_version, score_policy_version,
                       diversity_policy_version, exploration_policy_version,
                       result_fingerprint, final_ranked_candidate_count
                from public.recommendation_run
                where run_id in (?, ?)
                order by run_id
                """,
                baselineRunId,
                treatmentRunId);
        assertThat(runs).hasSize(2);
        Map<String, Object> baseline = runs.stream()
                .filter(row -> row.get("run_id").equals(baselineRunId)).findFirst().orElseThrow();
        Map<String, Object> treatment = runs.stream()
                .filter(row -> row.get("run_id").equals(treatmentRunId)).findFirst().orElseThrow();
        assertThat(treatment.get("run_mode")).isEqualTo("shadow");
        assertThat(treatment.get("run_status")).isEqualTo("succeeded");
        assertThat(treatment.get("user_id")).isEqualTo(baseline.get("user_id"));
        assertThat(treatment.get("session_id")).isEqualTo(baseline.get("session_id"));
        assertThat(treatment.get("context_id")).isEqualTo(baseline.get("context_id"));
        assertThat(treatment.get("reference_time")).isEqualTo(baseline.get("reference_time"));
        assertThat(treatment.get("ranking_policy_version")).isEqualTo(assignment.get("policy_bundle_version"));
        assertThat(treatment.get("score_policy_version")).isEqualTo(assignment.get("score_policy_version"));
        assertThat(((Number) treatment.get("final_ranked_candidate_count")).intValue()).isPositive();

        Map<String, Object> profile = jdbcTemplate.queryForMap(
                """
                select segment, explicit_preference_count, input_event_count,
                       accepted_event_count, ignored_event_count, duplicate_event_count,
                       signal_count, signals::text, fingerprint
                from public.recommendation_p1_profile_snapshot
                where profile_snapshot_id = ?
                """,
                assignment.get("profile_snapshot_id"));
        assertThat(profile.get("segment")).isEqualTo("explicit_only");
        assertThat(((Number) profile.get("explicit_preference_count")).intValue()).isEqualTo(1);
        assertThat(((Number) profile.get("accepted_event_count")).intValue()
                + ((Number) profile.get("ignored_event_count")).intValue()
                + ((Number) profile.get("duplicate_event_count")).intValue())
                .isEqualTo(((Number) profile.get("input_event_count")).intValue());
        assertThat(profile.get("signals").toString()).contains("theme:food");
        assertThat(profile.get("fingerprint").toString()).hasSize(64);

        Map<String, Object> comparison = jdbcTemplate.queryForMap(
                """
                select baseline_run_id, treatment_run_id, cutoff,
                       overlap_rate, treatment_unique_author_count,
                       treatment_unique_region_count, treatment_unique_theme_count,
                       treatment_low_exposure_share, treatment_top_author_share,
                       treatment_top_region_share, treatment_mean_adjusted_popularity,
                       comparison_fingerprint
                from public.recommendation_p1_comparison
                where treatment_run_id = ?
                """,
                treatmentRunId);
        assertThat(comparison.get("baseline_run_id")).isEqualTo(baselineRunId);
        assertThat(comparison.get("treatment_run_id")).isEqualTo(treatmentRunId);
        assertThat(((Number) comparison.get("cutoff")).intValue()).isEqualTo(20);
        assertThat(((Number) comparison.get("treatment_unique_region_count")).intValue()).isGreaterThanOrEqualTo(1);
        assertThat(comparison.get("comparison_fingerprint").toString()).hasSize(64);

        List<Map<String, Object>> candidates = jdbcTemplate.queryForList(
                """
                select absolute_rank, source_entity_id, score, score_policy_version, provenance::text
                from public.recommendation_run_candidate
                where run_id = ?
                order by absolute_rank
                """,
                treatmentRunId);
        assertThat(candidates).isNotEmpty();
        assertThat(candidates).allSatisfy(candidate -> {
            assertThat(candidate.get("score_policy_version")).isEqualTo("ranking-policy-v2-explicit");
            assertThat(candidate.get("provenance").toString())
                    .contains("adjustedPopularityScore", "lowExposureBoost", "appliedRelaxations");
        });
    }

    private void tag(JourneyPost post, String... slugs) {
        for (String slug : slugs) {
            jdbcTemplate.update(
                    """
                    insert into public.post_tags (post_id, tag_id)
                    select ?, t.id from public.tags t where t.slug = ?
                    """,
                    post.getId(),
                    slug);
        }
    }
}
