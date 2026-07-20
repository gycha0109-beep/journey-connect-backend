package com.jc.backend.recommendation.application;

import static com.jc.backend.CanonicalTestData.publishedPost;
import static com.jc.backend.CanonicalTestData.region;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jc.backend.CanonicalPostgresTest;
import com.jc.backend.database.DatabaseRequestIdentity;
import com.jc.backend.post.JourneyPostRepository;
import com.jc.backend.post.PlaceRepository;
import com.jc.backend.recommendation.config.RecommendationP1Properties;
import com.jc.backend.region.Region;
import com.jc.backend.region.RegionRepository;
import com.jc.backend.user.UserAccount;
import com.jc.backend.user.UserRepository;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@CanonicalPostgresTest
@Tag("p1-verification")
@TestPropertySource(properties = {
        "app.recommendation.mode=CANARY",
        "app.recommendation.canary-allocation-basis-points=10000",
        "app.recommendation.canary-cursor-secret=0123456789abcdef0123456789abcdef",
        "app.recommendation.canary-release-id=p0-canary-for-p1",
        "app.recommendation.p1.mode=CANARY",
        "app.recommendation.p1.canary-allocation-basis-points=10000",
        "app.recommendation.p1.release-id=p1-canary-integration-v1"
})
class RecommendationP1CanaryIntegrationTest {

    @Autowired private UserRepository users;
    @Autowired private RegionRepository regions;
    @Autowired private JourneyPostRepository posts;
    @Autowired private PlaceRepository places;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DatabaseRequestIdentity requestIdentity;
    @Autowired private RecommendationOrchestrationService orchestrationService;
    @Autowired private RecommendationReplayService replayService;
    @Autowired private RecommendationP1RuntimeService p1RuntimeService;
    @Autowired private RecommendationP1Properties p1Properties;

    @Test
    void canaryServesTreatmentOnlyForTheCohortAndOffRollsBackToBaseline() {
        UserAccount user = users.save(new UserAccount(
                "p1-canary-user@example.com", "hash", "p1-canary-user"));
        UserAccount author = users.save(new UserAccount(
                "p1-canary-author@example.com", "hash", "p1-canary-author"));
        Region seoul = region(regions, "KR-SEOUL");
        posts.saveAndFlush(publishedPost(
                places, author, seoul, "p1 canary route", "p1 canary content"));
        String sessionId = "p1-canary-session";

        RecommendationOrchestrationService.RunResult baseline;
        try (DatabaseRequestIdentity.Scope ignored = requestIdentity.open(user.getId())) {
            baseline = orchestrationService.runCanary(
                    new RecommendationOrchestrationService.CanaryRunRequest(user.getId(), sessionId));
        }
        assertThat(replayService.audit(baseline.runId()).exactMatch()).isTrue();

        P1Counts beforeFailure = p1Counts(baseline.runId());
        p1Properties.setReleaseId("@invalid-release");
        assertThatThrownBy(() -> p1RuntimeService.selectCanaryRun(
                baseline.runId(), user.getId(), sessionId))
                .isInstanceOf(RuntimeException.class);
        assertThat(p1Counts(baseline.runId())).isEqualTo(beforeFailure);

        p1Properties.setReleaseId("p1-canary-integration-v1");
        String treatmentRunId = p1RuntimeService.selectCanaryRun(
                baseline.runId(), user.getId(), sessionId);
        assertThat(treatmentRunId).isNotEqualTo(baseline.runId());

        Map<String, Object> treatment = jdbcTemplate.queryForMap(
                """
                select r.run_mode, r.run_status, r.user_id, r.session_id,
                       r.ranking_policy_version, r.score_policy_version,
                       a.baseline_run_id, a.release_id, a.experiment_assignment,
                       c.comparison_fingerprint
                from public.recommendation_run r
                join public.recommendation_p1_policy_assignment a
                  on a.treatment_run_id = r.run_id
                join public.recommendation_p1_comparison c
                  on c.treatment_run_id = r.run_id
                where r.run_id = ?
                """,
                treatmentRunId);
        assertThat(treatment.get("run_mode")).isEqualTo("canary");
        assertThat(treatment.get("run_status")).isEqualTo("succeeded");
        assertThat(treatment.get("baseline_run_id")).isEqualTo(baseline.runId());
        assertThat(treatment.get("release_id")).isEqualTo("p1-canary-integration-v1");
        assertThat(treatment.get("experiment_assignment")).isEqualTo("treatment");
        assertThat(treatment.get("ranking_policy_version").toString()).contains("p1-policy-bundle");
        assertThat(treatment.get("score_policy_version").toString()).startsWith("ranking-policy-v2");
        assertThat(treatment.get("comparison_fingerprint").toString()).hasSize(64);

        String repeatedTreatmentRunId = p1RuntimeService.selectCanaryRun(
                baseline.runId(), user.getId(), sessionId);
        assertThat(repeatedTreatmentRunId).isNotEqualTo(treatmentRunId);
        String repeatedFingerprint = jdbcTemplate.queryForObject(
                "select result_fingerprint from public.recommendation_run where run_id = ?",
                String.class,
                repeatedTreatmentRunId);
        String firstFingerprint = jdbcTemplate.queryForObject(
                "select result_fingerprint from public.recommendation_run where run_id = ?",
                String.class,
                treatmentRunId);
        assertThat(repeatedFingerprint).isEqualTo(firstFingerprint);

        p1Properties.setMode(RecommendationP1Properties.Mode.OFF);
        String rollbackRunId = p1RuntimeService.selectCanaryRun(
                baseline.runId(), user.getId(), sessionId);
        assertThat(rollbackRunId).isEqualTo(baseline.runId());
    }
    private P1Counts p1Counts(String baselineRunId) {
        Integer treatmentRuns = jdbcTemplate.queryForObject(
                """
                select count(*)::integer
                from public.recommendation_run
                where ranking_policy_version like 'p1-policy-bundle-v1:%'
                """,
                Integer.class);
        Integer profiles = jdbcTemplate.queryForObject(
                "select count(*)::integer from public.recommendation_p1_profile_snapshot",
                Integer.class);
        Integer assignments = jdbcTemplate.queryForObject(
                "select count(*)::integer from public.recommendation_p1_policy_assignment where baseline_run_id = ?",
                Integer.class,
                baselineRunId);
        Integer comparisons = jdbcTemplate.queryForObject(
                "select count(*)::integer from public.recommendation_p1_comparison where baseline_run_id = ?",
                Integer.class,
                baselineRunId);
        Integer snapshots = jdbcTemplate.queryForObject(
                "select count(*)::integer from public.recommendation_snapshot where schema_version = 'p1.0.0'",
                Integer.class);
        return new P1Counts(treatmentRuns, profiles, assignments, comparisons, snapshots);
    }

    private record P1Counts(
            int treatmentRuns,
            int profiles,
            int assignments,
            int comparisons,
            int snapshots) {
    }

}
