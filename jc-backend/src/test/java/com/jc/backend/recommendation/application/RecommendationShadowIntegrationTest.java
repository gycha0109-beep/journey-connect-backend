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
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@CanonicalPostgresTest
@TestPropertySource(properties = {
        "app.recommendation.mode=SHADOW",
        "app.recommendation.readiness-minimum-shadow-runs=1",
        "app.recommendation.readiness-lookback-runs=10",
        "app.recommendation.readiness-max-p95-duration-ms=60000"
})
class RecommendationShadowIntegrationTest {

    @Autowired private UserRepository users;
    @Autowired private RegionRepository regions;
    @Autowired private JourneyPostRepository posts;
    @Autowired private PlaceRepository places;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DatabaseRequestIdentity requestIdentity;
    @Autowired private RecommendationShadowService shadowService;
    @Autowired private RecommendationReplayService replayService;
    @Autowired private RecommendationCanaryReadinessService readinessService;

    @Test
    void authenticatedFirstPagePersistsExactReplayAuditAndBecomesCanaryReady() {
        UserAccount user = users.save(new UserAccount(
                "shadow-user@example.com", "hash", "shadow-user"));
        Region seoul = region(regions, "KR-SEOUL");
        JourneyPost post = posts.saveAndFlush(publishedPost(
                places, user, seoul, "shadow recommendation", "shadow content"));
        jdbcTemplate.update(
                """
                insert into public.post_tags (post_id, tag_id)
                select ?, t.id from public.tags t where t.slug in ('food', 'solo-travel')
                """,
                post.getId());

        RecommendationShadowService.ShadowOutcome outcome;
        try (DatabaseRequestIdentity.Scope ignored = requestIdentity.open(user.getId())) {
            outcome = shadowService.observeHomeFeed(user.getId(), "jwt-shadow-1", true);
        }

        assertThat(outcome.status())
                .isEqualTo(RecommendationShadowService.ShadowOutcome.Status.SUCCEEDED);
        assertThat(outcome.runId()).isNotBlank();

        Map<String, Object> run = jdbcTemplate.queryForMap(
                """
                select run_mode, run_status, user_id, session_id, surface,
                       input_count, scored_candidate_count,
                       final_ranked_candidate_count, terminal_candidate_count,
                       result_fingerprint
                from public.recommendation_run
                where run_id = ?
                """,
                outcome.runId());
        assertThat(run.get("run_mode")).isEqualTo("shadow");
        assertThat(run.get("run_status")).isEqualTo("succeeded");
        assertThat(((Number) run.get("user_id")).longValue()).isEqualTo(user.getId());
        assertThat(run.get("session_id")).isEqualTo("jwt-shadow-1");
        assertThat(run.get("surface")).isEqualTo("home");
        int input = ((Number) run.get("input_count")).intValue();
        int scored = ((Number) run.get("scored_candidate_count")).intValue();
        int ranked = ((Number) run.get("final_ranked_candidate_count")).intValue();
        int terminal = ((Number) run.get("terminal_candidate_count")).intValue();
        assertThat(input).isPositive().isEqualTo(ranked + terminal);
        assertThat(ranked).isBetween(scored, input);

        Map<String, Object> audit = jdbcTemplate.queryForMap(
                """
                select replay_status, mismatch_categories::text,
                       expected_result_fingerprint, actual_result_fingerprint,
                       ranked_candidate_count, terminal_candidate_count
                from public.recommendation_replay_audit
                where run_id = ?
                """,
                outcome.runId());
        assertThat(audit.get("replay_status")).isEqualTo("exact_match");
        assertThat(audit.get("mismatch_categories")).isEqualTo("[]");
        assertThat(audit.get("expected_result_fingerprint"))
                .isEqualTo(run.get("result_fingerprint"));
        assertThat(audit.get("actual_result_fingerprint"))
                .isEqualTo(run.get("result_fingerprint"));
        assertThat(((Number) audit.get("ranked_candidate_count")).intValue()).isEqualTo(ranked);
        assertThat(((Number) audit.get("terminal_candidate_count")).intValue()).isEqualTo(terminal);

        RecommendationReplayService.ReplayAuditResult repeated = replayService.audit(outcome.runId());
        assertThat(repeated.exactMatch()).isTrue();
        Integer auditCount = jdbcTemplate.queryForObject(
                "select count(*) from public.recommendation_replay_audit where run_id = ?",
                Integer.class,
                outcome.runId());
        assertThat(auditCount).isEqualTo(1);

        RecommendationCanaryReadinessService.ReadinessResult readiness = readinessService.evaluate();
        assertThat(readiness.ready()).isTrue();
        assertThat(readiness.blockers()).isEmpty();
        assertThat(readiness.evaluatedShadowRuns()).isEqualTo(1);
    }
}
