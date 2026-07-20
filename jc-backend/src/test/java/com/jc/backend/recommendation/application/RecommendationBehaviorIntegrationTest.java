package com.jc.backend.recommendation.application;

import static com.jc.backend.CanonicalTestData.publishedPost;
import static com.jc.backend.CanonicalTestData.region;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jc.backend.CanonicalPostgresTest;
import com.jc.backend.common.DomainException;
import com.jc.backend.database.DatabaseRequestIdentity;
import com.jc.backend.post.JourneyPost;
import com.jc.backend.post.JourneyPostRepository;
import com.jc.backend.post.PlaceRepository;
import com.jc.backend.region.Region;
import com.jc.backend.region.RegionRepository;
import com.jc.backend.user.UserAccount;
import com.jc.backend.user.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@CanonicalPostgresTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.recommendation.mode=CANARY",
        "app.recommendation.canary-allocation-basis-points=10000",
        "app.recommendation.canary-cursor-secret=0123456789abcdef0123456789abcdef",
        "app.recommendation.canary-release-id=release:p0-7-behavior-test",
        "app.recommendation.readiness-minimum-shadow-runs=1",
        "app.recommendation.readiness-lookback-runs=10",
        "app.recommendation.readiness-max-p95-duration-ms=60000"
})
class RecommendationBehaviorIntegrationTest {

    private static final String TOKEN_ID = "jwt-behavior-test";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository users;
    @Autowired private RegionRepository regions;
    @Autowired private JourneyPostRepository posts;
    @Autowired private PlaceRepository places;
    @Autowired private DatabaseRequestIdentity requestIdentity;
    @Autowired private RecommendationOrchestrationService orchestrationService;
    @Autowired private RecommendationReplayService replayService;
    @Autowired private RecommendationPostInteractionService interactionService;

    private UserAccount user;
    private List<Long> postIds;

    @BeforeEach
    void setUp() {
        user = users.save(new UserAccount(
                "behavior-api@example.com", "hash", "behavior-api"));
        Region seoul = region(regions, "KR-SEOUL");
        postIds = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            JourneyPost post = posts.saveAndFlush(publishedPost(
                    places, user, seoul, "behavior-" + index, "content-" + index));
            postIds.add(post.getId());
            jdbcTemplate.update(
                    """
                    insert into public.post_tags (post_id, tag_id)
                    select ?, t.id from public.tags t where t.slug in ('food', 'solo-travel')
                    """,
                    post.getId());
        }

        try (DatabaseRequestIdentity.Scope ignored = requestIdentity.open(user.getId())) {
            var shadow = orchestrationService.runShadow(
                    new RecommendationOrchestrationService.ShadowRunRequest(
                            user.getId(), TOKEN_ID));
            assertThat(replayService.audit(shadow.runId()).exactMatch()).isTrue();
        }
    }

    @Test
    void runBoundEventsAndAtomicPostInteractionsAreIdempotent() throws Exception {
        MvcResult feedResult = mockMvc.perform(get("/api/v1/feed")
                        .param("size", "2")
                        .with(jwt().jwt(token -> token
                                .subject(user.getId().toString())
                                .claim("jti", TOKEN_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recommendationRunId").isNotEmpty())
                .andReturn();

        JsonNode feed = objectMapper.readTree(feedResult.getResponse().getContentAsByteArray());
        String runId = feed.at("/data/recommendationRunId").asText();
        long postId = feed.at("/data/items/0/id").asLong();
        long secondPostId = feed.at("/data/items/1/id").asLong();
        Instant occurredAt = Instant.now().truncatedTo(ChronoUnit.MICROS);

        Map<String, Object> click = Map.of(
                "eventId", "behavior-click-1",
                "idempotencyKey", "behavior-click-key-1",
                "runId", runId,
                "eventType", "CLICK",
                "postId", postId,
                "occurredAt", occurredAt.toString(),
                "metadata", Map.of("position", 1, "surface", "home"));

        mockMvc.perform(post("/api/v1/recommendation/events")
                        .with(jwt().jwt(token -> token
                                .subject(user.getId().toString())
                                .claim("jti", TOKEN_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(click)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("stored"));

        mockMvc.perform(post("/api/v1/recommendation/events")
                        .with(jwt().jwt(token -> token
                                .subject(user.getId().toString())
                                .claim("jti", TOKEN_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(click)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("duplicate"));

        Map<String, Object> conflict = Map.of(
                "eventId", "behavior-click-conflict",
                "idempotencyKey", "behavior-click-key-1",
                "runId", runId,
                "eventType", "VIEW",
                "postId", postId,
                "occurredAt", occurredAt.toString(),
                "metadata", Map.of("surface", "detail"));
        mockMvc.perform(post("/api/v1/recommendation/events")
                        .with(jwt().jwt(token -> token
                                .subject(user.getId().toString())
                                .claim("jti", TOKEN_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(conflict)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));

        Map<String, Object> view = Map.of(
                "eventId", "behavior-view-1",
                "idempotencyKey", "behavior-view-key-1",
                "runId", runId,
                "eventType", "VIEW",
                "postId", postId,
                "occurredAt", occurredAt.plusMillis(500).toString(),
                "metadata", Map.of("surface", "detail"));
        mockMvc.perform(post("/api/v1/recommendation/events")
                        .with(jwt().jwt(token -> token
                                .subject(user.getId().toString())
                                .claim("jti", TOKEN_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(view)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("stored"));

        String interactionTime = occurredAt.plusSeconds(1).toString();
        mockMvc.perform(post("/api/v1/posts/{postId}/likes", postId)
                        .header("X-Recommendation-Run-Id", runId)
                        .header("X-Recommendation-Event-Id", "behavior-like-1")
                        .header("Idempotency-Key", "behavior-like-key-1")
                        .header("X-Recommendation-Occurred-At", interactionTime)
                        .with(jwt().jwt(token -> token
                                .subject(user.getId().toString())
                                .claim("jti", TOKEN_ID))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/posts/{postId}/likes", postId)
                        .header("X-Recommendation-Run-Id", runId)
                        .header("X-Recommendation-Event-Id", "behavior-like-1")
                        .header("Idempotency-Key", "behavior-like-key-1")
                        .header("X-Recommendation-Occurred-At", interactionTime)
                        .with(jwt().jwt(token -> token
                                .subject(user.getId().toString())
                                .claim("jti", TOKEN_ID))))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from public.post_likes where post_id = ? and user_id = ?",
                Integer.class,
                postId,
                user.getId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from public.recommendation_behavior_event where event_id = 'behavior-like-1'",
                Integer.class)).isEqualTo(1);

        mockMvc.perform(post("/api/v1/posts/{postId}/likes", secondPostId)
                        .header("X-Recommendation-Run-Id", runId)
                        .header("X-Recommendation-Event-Id", "behavior-like-conflict")
                        .header("Idempotency-Key", "behavior-like-key-1")
                        .header("X-Recommendation-Occurred-At", occurredAt.plusSeconds(2).toString())
                        .with(jwt().jwt(token -> token
                                .subject(user.getId().toString())
                                .claim("jti", TOKEN_ID))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from public.post_likes where post_id = ? and user_id = ?",
                Integer.class,
                secondPostId,
                user.getId())).isZero();

        mockMvc.perform(delete("/api/v1/posts/{postId}/likes", postId)
                        .header("X-Recommendation-Run-Id", runId)
                        .header("X-Recommendation-Event-Id", "behavior-unlike-1")
                        .header("Idempotency-Key", "behavior-unlike-key-1")
                        .header("X-Recommendation-Occurred-At", occurredAt.plusSeconds(3).toString())
                        .with(jwt().jwt(token -> token
                                .subject(user.getId().toString())
                                .claim("jti", TOKEN_ID))))
                .andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from public.post_likes where post_id = ? and user_id = ?",
                Integer.class,
                postId,
                user.getId())).isZero();

        String saveTime = occurredAt.plusSeconds(4).toString();
        mockMvc.perform(post("/api/v1/posts/{postId}/bookmarks", postId)
                        .header("X-Recommendation-Run-Id", runId)
                        .header("X-Recommendation-Event-Id", "behavior-save-1")
                        .header("Idempotency-Key", "behavior-save-key-1")
                        .header("X-Recommendation-Occurred-At", saveTime)
                        .with(jwt().jwt(token -> token
                                .subject(user.getId().toString())
                                .claim("jti", TOKEN_ID))))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/posts/{postId}/bookmarks", postId)
                        .header("X-Recommendation-Run-Id", runId)
                        .header("X-Recommendation-Event-Id", "behavior-save-1")
                        .header("Idempotency-Key", "behavior-save-key-1")
                        .header("X-Recommendation-Occurred-At", saveTime)
                        .with(jwt().jwt(token -> token
                                .subject(user.getId().toString())
                                .claim("jti", TOKEN_ID))))
                .andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from public.bookmarks where post_id = ? and user_id = ?",
                Integer.class,
                postId,
                user.getId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from public.recommendation_behavior_event where event_id = 'behavior-save-1'",
                Integer.class)).isEqualTo(1);

        mockMvc.perform(delete("/api/v1/posts/{postId}/bookmarks", postId)
                        .header("X-Recommendation-Run-Id", runId)
                        .header("X-Recommendation-Event-Id", "behavior-unsave-1")
                        .header("Idempotency-Key", "behavior-unsave-key-1")
                        .header("X-Recommendation-Occurred-At", occurredAt.plusSeconds(5).toString())
                        .with(jwt().jwt(token -> token
                                .subject(user.getId().toString())
                                .claim("jti", TOKEN_ID))))
                .andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from public.bookmarks where post_id = ? and user_id = ?",
                Integer.class,
                postId,
                user.getId())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from public.recommendation_behavior_event where event_id in ('behavior-unlike-1', 'behavior-unsave-1')",
                Integer.class)).isEqualTo(2);
    }


    @Test
    void concurrentConflictingIdempotencyKeepsExactlyOneMutationAndEvent() throws Exception {
        Instant occurredAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        String key = "behavior-concurrent-key";
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() -> concurrentLike(
                    postIds.get(0), "behavior-concurrent-a", key, occurredAt, ready, start));
            Future<String> second = executor.submit(() -> concurrentLike(
                    postIds.get(1), "behavior-concurrent-b", key, occurredAt, ready, start));
            ready.await();
            start.countDown();

            List<String> outcomes = List.of(first.get(), second.get());
            assertThat(outcomes).containsExactlyInAnyOrder("applied", "IDEMPOTENCY_CONFLICT");
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from public.post_likes where user_id = ? and post_id in (?, ?)",
                    Integer.class,
                    user.getId(),
                    postIds.get(0),
                    postIds.get(1))).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from public.recommendation_behavior_event where idempotency_key = ?",
                    Integer.class,
                    key)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private String concurrentLike(
            long postId,
            String eventId,
            String key,
            Instant occurredAt,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        try (DatabaseRequestIdentity.Scope ignored = requestIdentity.open(user.getId())) {
            try {
                interactionService.apply(
                        user.getId(),
                        TOKEN_ID,
                        postId,
                        com.jc.backend.recommendation.persistence.RecommendationPostInteractionStore.Action.LIKE,
                        new RecommendationPostInteractionService.TrackingContext(
                                null, eventId, key, occurredAt));
                return "applied";
            } catch (DomainException exception) {
                return exception.getCode();
            }
        }
    }

    @Test
    void runBindingRejectsDifferentUserAndSessionWithoutMutation() throws Exception {
        MvcResult feedResult = mockMvc.perform(get("/api/v1/feed")
                        .param("size", "2")
                        .with(jwt().jwt(token -> token
                                .subject(user.getId().toString())
                                .claim("jti", TOKEN_ID))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode feed = objectMapper.readTree(feedResult.getResponse().getContentAsByteArray());
        String runId = feed.at("/data/recommendationRunId").asText();
        long postId = feed.at("/data/items/0/id").asLong();
        Instant occurredAt = Instant.now().truncatedTo(ChronoUnit.MICROS);

        Map<String, Object> event = Map.of(
                "eventId", "behavior-session-mismatch",
                "idempotencyKey", "behavior-session-mismatch-key",
                "runId", runId,
                "eventType", "CLICK",
                "postId", postId,
                "occurredAt", occurredAt.toString(),
                "metadata", Map.of("surface", "home"));
        mockMvc.perform(post("/api/v1/recommendation/events")
                        .with(jwt().jwt(token -> token
                                .subject(user.getId().toString())
                                .claim("jti", "different-session")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(event)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RECOMMENDATION_EVENT_BINDING_INVALID"));

        UserAccount other = users.save(new UserAccount(
                "behavior-other@example.com", "hash", "behavior-other"));
        Map<String, Object> otherUserEvent = Map.of(
                "eventId", "behavior-user-mismatch",
                "idempotencyKey", "behavior-user-mismatch-key",
                "runId", runId,
                "eventType", "VIEW",
                "postId", postId,
                "occurredAt", occurredAt.toString(),
                "metadata", Map.of("surface", "home"));
        mockMvc.perform(post("/api/v1/recommendation/events")
                        .with(jwt().jwt(token -> token
                                .subject(other.getId().toString())
                                .claim("jti", TOKEN_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(otherUserEvent)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RECOMMENDATION_EVENT_BINDING_INVALID"));

        mockMvc.perform(post("/api/v1/posts/{postId}/likes", postId)
                        .header("X-Recommendation-Run-Id", runId)
                        .header("X-Recommendation-Event-Id", "behavior-like-session-mismatch")
                        .header("Idempotency-Key", "behavior-like-session-mismatch-key")
                        .header("X-Recommendation-Occurred-At", occurredAt.toString())
                        .with(jwt().jwt(token -> token
                                .subject(user.getId().toString())
                                .claim("jti", "different-session"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RECOMMENDATION_INTERACTION_BINDING_INVALID"));
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from public.post_likes where post_id = ? and user_id = ?",
                Integer.class,
                postId,
                user.getId())).isZero();
    }

    @Test
    void runCandidateBindingRejectsUnrankedPost() throws Exception {
        MvcResult feedResult = mockMvc.perform(get("/api/v1/feed")
                        .param("size", "1")
                        .with(jwt().jwt(token -> token
                                .subject(user.getId().toString())
                                .claim("jti", TOKEN_ID))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode feed = objectMapper.readTree(feedResult.getResponse().getContentAsByteArray());
        String runId = feed.at("/data/recommendationRunId").asText();

        Map<String, Object> invalid = Map.of(
                "eventId", "behavior-invalid-candidate",
                "idempotencyKey", "behavior-invalid-candidate-key",
                "runId", runId,
                "eventType", "CLICK",
                "postId", Long.MAX_VALUE,
                "occurredAt", Instant.now().toString(),
                "metadata", Map.of("surface", "home"));

        mockMvc.perform(post("/api/v1/recommendation/events")
                        .with(jwt().jwt(token -> token
                                .subject(user.getId().toString())
                                .claim("jti", TOKEN_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(invalid)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RECOMMENDATION_EVENT_BINDING_INVALID"));
    }
}
