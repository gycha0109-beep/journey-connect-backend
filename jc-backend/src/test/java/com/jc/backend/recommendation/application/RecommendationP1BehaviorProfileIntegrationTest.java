package com.jc.backend.recommendation.application;

import static com.jc.backend.CanonicalTestData.publishedPost;
import static com.jc.backend.CanonicalTestData.region;
import static org.assertj.core.api.Assertions.assertThat;

import com.jc.backend.CanonicalPostgresTest;
import com.jc.backend.database.DatabaseRequestIdentity;
import com.jc.backend.post.JourneyPost;
import com.jc.backend.post.JourneyPostRepository;
import com.jc.backend.post.PlaceRepository;
import com.jc.backend.recommendation.api.RecommendationBehaviorDtos;
import com.jc.backend.region.Region;
import com.jc.backend.region.RegionRepository;
import com.jc.backend.user.UserAccount;
import com.jc.backend.user.UserRepository;
import java.sql.Timestamp;
import java.time.Instant;
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
        "app.recommendation.p1.release-id=p1-behavior-profile-v1",
        "app.recommendation.p1.retrieval-limit=100",
        "app.recommendation.p1.core-candidate-limit=100"
})
class RecommendationP1BehaviorProfileIntegrationTest {

    @Autowired private UserRepository users;
    @Autowired private RegionRepository regions;
    @Autowired private JourneyPostRepository posts;
    @Autowired private PlaceRepository places;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DatabaseRequestIdentity requestIdentity;
    @Autowired private RecommendationOrchestrationService orchestrationService;
    @Autowired private RecommendationBehaviorService behaviorService;
    @Autowired private RecommendationP1RuntimeService p1RuntimeService;

    @Test
    void persistedBehaviorIsValidatedMappedDecayedAndBoundToAnEmergingProfile() {
        UserAccount user = users.save(new UserAccount(
                "p1-behavior-user@example.com", "hash", "p1-behavior-user"));
        UserAccount author = users.save(new UserAccount(
                "p1-behavior-author@example.com", "hash", "p1-behavior-author"));
        Region seoul = region(regions, "KR-SEOUL");
        JourneyPost post = posts.saveAndFlush(publishedPost(
                places, author, seoul, "history running route", "behavior profile content"));
        for (String slug : new String[] {"history", "running"}) {
            jdbcTemplate.update(
                    """
                    insert into public.post_tags (post_id, tag_id)
                    select ?, id from public.tags where slug = ?
                    """,
                    post.getId(),
                    slug);
        }

        String tokenId = "p1-behavior-token";
        String sessionId = RecommendationSessionIds.fromJwt(user.getId(), tokenId);
        RecommendationOrchestrationService.RunResult baseline;
        try (DatabaseRequestIdentity.Scope ignored = requestIdentity.open(user.getId())) {
            baseline = orchestrationService.runShadow(
                    new RecommendationOrchestrationService.ShadowRunRequest(user.getId(), sessionId));
        }
        Instant referenceTime = jdbcTemplate.queryForObject(
                "select reference_time from public.recommendation_run where run_id = ?",
                (resultSet, rowNumber) -> resultSet.getTimestamp(1).toInstant(),
                baseline.runId());
        assertThat(referenceTime).isNotNull();

        try (DatabaseRequestIdentity.Scope ignored = requestIdentity.open(user.getId())) {
            behaviorService.record(
                    user.getId(),
                    tokenId,
                    new RecommendationBehaviorDtos.EventRequest(
                            "p1-behavior-click-1",
                            "p1-behavior-click-key-1",
                            baseline.runId(),
                            RecommendationBehaviorDtos.EventType.SHARE,
                            post.getId(),
                            referenceTime.minusSeconds(1),
                            Map.of("position", 1)));
        }

        RecommendationP1RuntimeService.TreatmentOutcome outcome = p1RuntimeService.observeShadow(
                baseline.runId(), user.getId(), sessionId);
        assertThat(outcome.status()).isEqualTo(RecommendationP1RuntimeService.Status.SUCCEEDED);
        assertThat(outcome.segment()).isEqualTo("emerging");

        Map<String, Object> profile = jdbcTemplate.queryForMap(
                """
                select p.segment, p.accepted_event_count, p.ignored_event_count,
                       p.duplicate_event_count, p.signals::text, p.fingerprint,
                       a.profile_policy_version, a.feature_vocabulary_version
                from public.recommendation_p1_policy_assignment a
                join public.recommendation_p1_profile_snapshot p
                  on p.profile_snapshot_id = a.profile_snapshot_id
                where a.treatment_run_id = ?
                """,
                outcome.treatmentRunId());
        assertThat(profile.get("segment")).isEqualTo("emerging");
        assertThat(((Number) profile.get("accepted_event_count")).intValue()).isEqualTo(1);
        assertThat(((Number) profile.get("ignored_event_count")).intValue()).isZero();
        assertThat(((Number) profile.get("duplicate_event_count")).intValue()).isZero();
        assertThat(profile.get("signals").toString())
                .contains("theme:history", "activity:running", "BEHAVIOR");
        assertThat(profile.get("profile_policy_version")).isEqualTo("behavior-profile-policy-v1");
        assertThat(profile.get("feature_vocabulary_version")).isEqualTo("feature-vocabulary-v2");
        assertThat(profile.get("fingerprint").toString()).hasSize(64);

        Timestamp occurredAt = jdbcTemplate.queryForObject(
                "select occurred_at from public.recommendation_behavior_event where event_id = 'p1-behavior-click-1'",
                Timestamp.class);
        assertThat(occurredAt.toInstant()).isEqualTo(referenceTime.minusSeconds(1));
    }
}
