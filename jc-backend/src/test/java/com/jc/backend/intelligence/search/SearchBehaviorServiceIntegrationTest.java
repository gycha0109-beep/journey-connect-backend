package com.jc.backend.intelligence.search;

import static com.jc.backend.CanonicalTestData.publishedPost;
import static com.jc.backend.CanonicalTestData.region;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jc.backend.CanonicalPostgresTest;
import com.jc.backend.common.DomainException;
import com.jc.backend.post.JourneyPost;
import com.jc.backend.post.JourneyPostRepository;
import com.jc.backend.post.PlaceRepository;
import com.jc.backend.region.Region;
import com.jc.backend.region.RegionRepository;
import com.jc.backend.user.UserAccount;
import com.jc.backend.user.UserRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

@CanonicalPostgresTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SearchBehaviorServiceIntegrationTest {

    private static final String SECRET =
            "ip125-test-only-jwt-secret-0123456789abcdef0123456789abcdef";

    @Autowired private SearchBehaviorService behaviorService;
    @Autowired private UserRepository users;
    @Autowired private RegionRepository regions;
    @Autowired private JourneyPostRepository posts;
    @Autowired private PlaceRepository places;
    @Autowired private JdbcTemplate jdbcTemplate;

    private UserAccount viewer;
    private JourneyPost post;

    @BeforeEach
    void setUp() {
        viewer = users.save(new UserAccount(
                "search-behavior-viewer@example.com", "hash", "search-behavior-viewer"));
        UserAccount author = users.save(new UserAccount(
                "search-behavior-author@example.com", "hash", "search-behavior-author"));
        Region seoul = region(regions, "KR-SEOUL");
        post = posts.save(publishedPost(
                places,
                author,
                seoul,
                "검색 행동 테스트",
                "검색 결과 행동 사실 저장"));
    }

    @Test
    void storesIdempotentSearchBehaviorWithoutRecommendationExposure() {
        Instant occurredAt = Instant.now().minusSeconds(1);
        String context = resultContext(occurredAt, post.getId(), 3);
        long exposureBefore = count("recommendation_exposure_event");
        var request = new SearchBehaviorDtos.EventRequest(
                "search-event-1",
                "search-idempotency-1",
                context,
                SearchBehaviorDtos.EventType.CLICK,
                post.getId(),
                3,
                occurredAt);

        var stored = behaviorService.record(viewer.getId(), "search-token-1", request);
        var duplicate = behaviorService.record(viewer.getId(), "search-token-1", request);

        assertThat(stored.status()).isEqualTo("stored");
        assertThat(duplicate.status()).isEqualTo("duplicate");
        assertThat(count("recommendation_behavior_event")).isEqualTo(1);
        assertThat(count("recommendation_exposure_event")).isEqualTo(exposureBefore);
        var row = jdbcTemplate.queryForMap(
                """
                select schema_version, run_id, event_type, entity_type, source_entity_id,
                       metadata::text, convert_from(canonical_payload, 'UTF8') as payload
                from public.recommendation_behavior_event
                where event_id = ?
                """,
                "search-event-1");
        assertThat(row.get("schema_version")).isEqualTo(SearchBehaviorService.SCHEMA_VERSION);
        assertThat(row.get("run_id")).isNull();
        assertThat(row.get("event_type")).isEqualTo("click");
        assertThat(row.get("entity_type")).isEqualTo("post");
        assertThat(((Number) row.get("source_entity_id")).longValue()).isEqualTo(post.getId());
        assertThat((String) row.get("metadata"))
                .contains("\"surface\": \"search\"")
                .contains("\"absoluteRank\": 3")
                .doesNotContain("검색 행동 테스트");
        assertThat((String) row.get("payload"))
                .contains("search-behavior-event-v1")
                .doesNotContain("검색 행동 테스트");
    }

    @Test
    void rejectsPostOrRankOutsideSignedResultPage() {
        Instant occurredAt = Instant.now().minusSeconds(1);
        String context = resultContext(occurredAt, post.getId(), 3);
        var request = new SearchBehaviorDtos.EventRequest(
                "search-event-invalid-rank",
                "search-idempotency-invalid-rank",
                context,
                SearchBehaviorDtos.EventType.VIEW,
                post.getId(),
                4,
                occurredAt);

        assertThatThrownBy(() -> behaviorService.record(
                viewer.getId(), "search-token-1", request))
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).getCode())
                .isEqualTo("SEARCH_RESULT_BINDING_INVALID");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from public.recommendation_behavior_event where event_id = ?",
                Long.class,
                "search-event-invalid-rank")).isZero();
    }

    @Test
    void rejectsResultContextBoundToAnotherUser() {
        Instant occurredAt = Instant.now().minusSeconds(1);
        String context = resultContext(occurredAt, post.getId(), 1);
        UserAccount another = users.save(new UserAccount(
                "search-behavior-other@example.com", "hash", "search-behavior-other"));
        var request = new SearchBehaviorDtos.EventRequest(
                "search-event-wrong-user",
                "search-idempotency-wrong-user",
                context,
                SearchBehaviorDtos.EventType.IMPRESSION,
                post.getId(),
                1,
                occurredAt);

        assertThatThrownBy(() -> behaviorService.record(
                another.getId(), "search-token-2", request))
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).getCode())
                .isEqualTo("SEARCH_RESULT_CONTEXT_INVALID");
    }

    private String resultContext(Instant issuedAt, long postId, int rank) {
        SearchContextCodec codec = new SearchContextCodec(SECRET, 900);
        String queryFingerprint = "a".repeat(64);
        String snapshotFingerprint = "b".repeat(64);
        String snapshotToken = codec.encodeSnapshot(
                "search:behavior-run",
                viewer.getId(),
                queryFingerprint,
                issuedAt,
                20,
                snapshotFingerprint,
                SearchRankingPolicy.POLICY_VERSION,
                issuedAt);
        var snapshot = codec.decodeSnapshot(
                snapshotToken,
                viewer.getId(),
                queryFingerprint,
                20,
                issuedAt.plusMillis(1));
        return codec.encodeResultContext(
                snapshot,
                List.of(new SearchContextCodec.ResultBinding(postId, rank)),
                issuedAt);
    }

    private long count(String table) {
        return jdbcTemplate.queryForObject(
                "select count(*) from public." + table,
                Long.class);
    }
}
