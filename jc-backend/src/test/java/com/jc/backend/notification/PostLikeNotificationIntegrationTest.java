package com.jc.backend.notification;

import static com.jc.backend.CanonicalTestData.publishedPost;
import static com.jc.backend.CanonicalTestData.region;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jc.backend.CanonicalPostgresTest;
import com.jc.backend.post.JourneyPost;
import com.jc.backend.post.JourneyPostRepository;
import com.jc.backend.post.PlaceRepository;
import com.jc.backend.region.Region;
import com.jc.backend.region.RegionRepository;
import com.jc.backend.user.UserAccount;
import com.jc.backend.user.UserRepository;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@CanonicalPostgresTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PostLikeNotificationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;
    @Autowired private RegionRepository regions;
    @Autowired private JourneyPostRepository posts;
    @Autowired private PlaceRepository places;

    private UserAccount author;
    private UserAccount liker;
    private Region seoul;
    private JourneyPost post;

    @BeforeEach
    void setUp() {
        author = users.save(new UserAccount("pf10-author@example.com", "hash", "pf10-author"));
        liker = users.save(new UserAccount("pf10-liker@example.com", "hash", "pf10-liker"));
        seoul = region(regions, "KR-SEOUL");
        post = posts.save(publishedPost(places, author, seoul, "PF10 like target", "like target body"));
    }

    @Test
    void appliedLikeCreatesExactPostLikeNotificationAndCanonicalBehaviorEvent() throws Exception {
        mockMvc.perform(likeRequest(post.getId(), liker.getId(), "pf10-token-1"))
                .andExpect(status().isNoContent());

        Map<String, Object> notification = jdbc.queryForMap("""
                select recipient_id, actor_id, type, target_type, target_id, dedupe_key
                from public.user_notifications
                where dedupe_key = ?
                """, dedupe(post.getId(), liker.getId()));

        assertThat(((Number) notification.get("recipient_id")).longValue()).isEqualTo(author.getId());
        assertThat(((Number) notification.get("actor_id")).longValue()).isEqualTo(liker.getId());
        assertThat(notification.get("type")).isEqualTo("post_like");
        assertThat(notification.get("target_type")).isEqualTo("post");
        assertThat(((Number) notification.get("target_id")).longValue()).isEqualTo(post.getId());
        assertThat(notification.get("dedupe_key")).isEqualTo(dedupe(post.getId(), liker.getId()));
        assertThat(likeStateCount(post.getId(), liker.getId())).isEqualTo(1L);
        assertThat(likeBehaviorCount(post.getId(), liker.getId())).isEqualTo(1L);
    }

    @Test
    void noChangeDuplicateLikeDoesNotCreateSecondNotificationOrBehaviorEvent() throws Exception {
        mockMvc.perform(likeRequest(post.getId(), liker.getId(), "pf10-token-2"))
                .andExpect(status().isNoContent());
        mockMvc.perform(likeRequest(post.getId(), liker.getId(), "pf10-token-2"))
                .andExpect(status().isNoContent());

        assertThat(notificationCount(post.getId(), liker.getId())).isEqualTo(1L);
        assertThat(likeStateCount(post.getId(), liker.getId())).isEqualTo(1L);
        assertThat(likeBehaviorCount(post.getId(), liker.getId())).isEqualTo(1L);
    }

    @Test
    void unlikeThenRelikeKeepsOneInboxRowForActorPostPair() throws Exception {
        mockMvc.perform(likeRequest(post.getId(), liker.getId(), "pf10-token-3"))
                .andExpect(status().isNoContent());
        mockMvc.perform(unlikeRequest(post.getId(), liker.getId(), "pf10-token-3"))
                .andExpect(status().isNoContent());
        mockMvc.perform(likeRequest(post.getId(), liker.getId(), "pf10-token-3"))
                .andExpect(status().isNoContent());

        assertThat(notificationCount(post.getId(), liker.getId())).isEqualTo(1L);
        assertThat(likeStateCount(post.getId(), liker.getId())).isEqualTo(1L);
        assertThat(likeBehaviorCount(post.getId(), liker.getId())).isEqualTo(2L);
        assertThat(unlikeBehaviorCount(post.getId(), liker.getId())).isEqualTo(1L);
    }

    @Test
    void selfLikeAppliesCanonicalInteractionButSuppressesNotification() throws Exception {
        JourneyPost ownPost = posts.save(publishedPost(
                places, liker, seoul, "PF10 self like", "self like body"));

        mockMvc.perform(likeRequest(ownPost.getId(), liker.getId(), "pf10-token-4"))
                .andExpect(status().isNoContent());

        assertThat(notificationCount(ownPost.getId(), liker.getId())).isZero();
        assertThat(likeStateCount(ownPost.getId(), liker.getId())).isEqualTo(1L);
        assertThat(likeBehaviorCount(ownPost.getId(), liker.getId())).isEqualTo(1L);
    }

    @Test
    void notificationConstraintFailureRollsBackLikeStateAndBehaviorEvent() throws Exception {
        jdbc.execute("""
                alter table public.user_notifications
                add constraint pf10_force_post_like_notification_failure
                check (type <> 'post_like')
                """);
        try {
            mockMvc.perform(likeRequest(post.getId(), liker.getId(), "pf10-token-5"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DATA_CONFLICT"));
        } finally {
            jdbc.execute("""
                    alter table public.user_notifications
                    drop constraint if exists pf10_force_post_like_notification_failure
                    """);
        }

        assertThat(notificationCount(post.getId(), liker.getId())).isZero();
        assertThat(likeStateCount(post.getId(), liker.getId())).isZero();
        assertThat(likeBehaviorCount(post.getId(), liker.getId())).isZero();
    }

    private MockHttpServletRequestBuilder likeRequest(long postId, long userId, String tokenId) {
        return post("/api/v1/posts/{postId}/likes", postId)
                .with(jwt().jwt(token -> token
                        .subject(Long.toString(userId))
                        .claim("jti", tokenId)));
    }

    private MockHttpServletRequestBuilder unlikeRequest(long postId, long userId, String tokenId) {
        return delete("/api/v1/posts/{postId}/likes", postId)
                .with(jwt().jwt(token -> token
                        .subject(Long.toString(userId))
                        .claim("jti", tokenId)));
    }

    private long notificationCount(long postId, long actorId) {
        return count("""
                select count(*) from public.user_notifications
                where type = 'post_like'
                  and target_type = 'post'
                  and target_id = ?
                  and actor_id = ?
                """, postId, actorId);
    }

    private long likeStateCount(long postId, long userId) {
        return count(
                "select count(*) from public.post_likes where post_id = ? and user_id = ?",
                postId,
                userId);
    }

    private long likeBehaviorCount(long postId, long userId) {
        return behaviorCount("like", postId, userId);
    }

    private long unlikeBehaviorCount(long postId, long userId) {
        return behaviorCount("unlike", postId, userId);
    }

    private long behaviorCount(String eventType, long postId, long userId) {
        return count("""
                select count(*) from public.recommendation_behavior_event
                where event_type = ?
                  and entity_type = 'post'
                  and source_entity_id = ?
                  and user_id = ?
                """, eventType, postId, userId);
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private String dedupe(long postId, long actorId) {
        return "post_like:" + postId + ":" + actorId;
    }
}
