package com.jc.backend.recommendation.application;

import static com.jc.backend.CanonicalTestData.publishedPost;
import static com.jc.backend.CanonicalTestData.region;
import static org.assertj.core.api.Assertions.assertThat;

import com.jc.backend.CanonicalPostgresTest;
import com.jc.backend.common.CursorPageResponse;
import com.jc.backend.database.DatabaseRequestIdentity;
import com.jc.backend.post.JourneyPost;
import com.jc.backend.post.JourneyPostRepository;
import com.jc.backend.post.PlaceRepository;
import com.jc.backend.post.PostDtos;
import com.jc.backend.region.Region;
import com.jc.backend.region.RegionRepository;
import com.jc.backend.user.UserAccount;
import com.jc.backend.user.UserRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@CanonicalPostgresTest
@TestPropertySource(properties = {
        "app.recommendation.mode=CANARY",
        "app.recommendation.canary-allocation-basis-points=10000",
        "app.recommendation.canary-cursor-secret=0123456789abcdef0123456789abcdef",
        "app.recommendation.canary-release-id=release:p0-6-test",
        "app.recommendation.readiness-minimum-shadow-runs=1",
        "app.recommendation.readiness-lookback-runs=10",
        "app.recommendation.readiness-max-p95-duration-ms=60000"
})
class RecommendationCanaryIntegrationTest {

    @Autowired private UserRepository users;
    @Autowired private RegionRepository regions;
    @Autowired private JourneyPostRepository posts;
    @Autowired private PlaceRepository places;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DatabaseRequestIdentity requestIdentity;
    @Autowired private RecommendationOrchestrationService orchestrationService;
    @Autowired private RecommendationReplayService replayService;
    @Autowired private RecommendationCanaryService canaryService;

    @Test
    void canaryServesPersistedPagesAndStoresIdempotentExposure() {
        UserAccount user = users.save(new UserAccount(
                "canary-user@example.com", "hash", "canary-user"));
        Region seoul = region(regions, "KR-SEOUL");
        List<Long> createdIds = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            JourneyPost post = posts.saveAndFlush(publishedPost(
                    places, user, seoul, "canary-" + index, "content-" + index));
            createdIds.add(post.getId());
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
                            user.getId(), "jwt-canary-1"));
            assertThat(replayService.audit(shadow.runId()).exactMatch()).isTrue();

            CursorPageResponse<PostDtos.Summary> first = canaryService
                    .firstPage(user.getId(), "jwt-canary-1", 1)
                    .orElseThrow();
            assertThat(first.items()).hasSize(1);
            assertThat(first.hasNext()).isTrue();
            assertThat(first.nextCursor()).startsWith(RecommendationCursorCodec.PREFIX);

            CursorPageResponse<PostDtos.Summary> second = canaryService.nextPage(
                    first.nextCursor(), user.getId(), "jwt-canary-1", 1);
            CursorPageResponse<PostDtos.Summary> repeated = canaryService.nextPage(
                    first.nextCursor(), user.getId(), "jwt-canary-1", 1);
            assertThat(second.items()).hasSize(1);
            assertThat(repeated).isEqualTo(second);
            assertThat(second.items().get(0).id()).isNotEqualTo(first.items().get(0).id());
        }

        List<String> modes = jdbcTemplate.queryForList(
                "select run_mode from public.recommendation_run order by created_at",
                String.class);
        assertThat(modes).contains("shadow", "canary");
        Integer canaryRuns = jdbcTemplate.queryForObject(
                "select count(*) from public.recommendation_run where run_mode = 'canary'",
                Integer.class);
        Integer exposures = jdbcTemplate.queryForObject(
                "select count(*) from public.recommendation_exposure_event",
                Integer.class);
        Integer exposureCandidates = jdbcTemplate.queryForObject(
                "select count(*) from public.recommendation_exposure_candidate",
                Integer.class);
        assertThat(canaryRuns).isEqualTo(1);
        assertThat(exposures).isEqualTo(2);
        assertThat(exposureCandidates).isEqualTo(2);
    }
}
