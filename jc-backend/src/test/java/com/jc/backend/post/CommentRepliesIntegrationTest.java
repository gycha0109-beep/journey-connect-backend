package com.jc.backend.post;

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
import com.jc.backend.region.Region;
import com.jc.backend.region.RegionRepository;
import com.jc.backend.user.UserAccount;
import com.jc.backend.user.UserRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@CanonicalPostgresTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CommentRepliesIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository users;
    @Autowired private RegionRepository regions;
    @Autowired private JourneyPostRepository posts;
    @Autowired private PlaceRepository places;

    private UserAccount owner;
    private UserAccount replier;
    private JourneyPost firstPost;
    private JourneyPost secondPost;

    @BeforeEach
    void setUp() {
        owner = users.save(new UserAccount("pf7-owner@example.com", "hash", "pf7-owner"));
        replier = users.save(new UserAccount("pf7-replier@example.com", "hash", "pf7-replier"));
        Region seoul = region(regions, "KR-SEOUL");
        firstPost = posts.save(publishedPost(places, owner, seoul, "PF7 first", "first"));
        secondPost = posts.save(publishedPost(places, owner, seoul, "PF7 second", "second"));
    }

    @Test
    void contentOnlyTopLevelAndValidReplyRoundTripAsFlatPage() throws Exception {
        JsonNode parent = createComment(owner, firstPost, "top-level", null, false);
        long parentId = parent.path("id").asLong();
        assertThat(parent.has("parentCommentId")).isTrue();
        assertThat(parent.path("parentCommentId").isNull()).isTrue();

        JsonNode reply = createComment(replier, firstPost, "reply", parentId, true);
        long replyId = reply.path("id").asLong();
        assertThat(reply.path("parentCommentId").asLong()).isEqualTo(parentId);

        JsonNode page = readComments(firstPost);
        assertThat(page.path("items").size()).isEqualTo(2);
        JsonNode readParent = item(page, parentId);
        JsonNode readReply = item(page, replyId);
        assertThat(readParent.path("parentCommentId").isNull()).isTrue();
        assertThat(readReply.path("parentCommentId").asLong()).isEqualTo(parentId);
    }

    @Test
    void missingCrossPostAndReplyToReplyParentsAreRejected() throws Exception {
        createCommentExpectBadRequest(replier, firstPost, "missing", Long.MAX_VALUE);

        long crossPostParentId = createComment(owner, secondPost, "other-post-parent", null, false)
                .path("id")
                .asLong();
        createCommentExpectBadRequest(replier, firstPost, "cross-post", crossPostParentId);

        long parentId = createComment(owner, firstPost, "parent", null, false)
                .path("id")
                .asLong();
        long replyId = createComment(replier, firstPost, "first-depth", parentId, true)
                .path("id")
                .asLong();
        createCommentExpectBadRequest(owner, firstPost, "second-depth", replyId);
    }

    @Test
    void authorDeletedParentRejectsNewReplyButExistingChildKeepsParentReference() throws Exception {
        long parentId = createComment(owner, firstPost, "parent", null, false)
                .path("id")
                .asLong();
        long childId = createComment(replier, firstPost, "existing-child", parentId, true)
                .path("id")
                .asLong();

        jdbcTemplate.update("update comments set deleted_at = current_timestamp where id = ?", parentId);
        try {
            createCommentExpectBadRequest(replier, firstPost, "blocked-child", parentId);
            JsonNode page = readComments(firstPost);
            assertThat(itemOrNull(page, parentId)).isNull();
            assertThat(item(page, childId).path("parentCommentId").asLong()).isEqualTo(parentId);
        } finally {
            jdbcTemplate.update("update comments set deleted_at = null where id = ?", parentId);
        }
    }

    @Test
    void moderationDeletedParentRejectsNewReply() throws Exception {
        long parentId = createComment(owner, firstPost, "moderated-parent", null, false)
                .path("id")
                .asLong();

        jdbcTemplate.update(
                "update comments set moderation_deleted_at = current_timestamp where id = ?",
                parentId);
        try {
            createCommentExpectBadRequest(replier, firstPost, "blocked-by-moderation", parentId);
        } finally {
            jdbcTemplate.update(
                    "update comments set moderation_deleted_at = null where id = ?",
                    parentId);
        }
    }

    @Test
    void replyAuthorCanDeleteOwnReplyWithoutMutatingParentLink() throws Exception {
        long parentId = createComment(owner, firstPost, "parent", null, false)
                .path("id")
                .asLong();
        long replyId = createComment(replier, firstPost, "deletable-reply", parentId, true)
                .path("id")
                .asLong();

        try {
            mockMvc.perform(delete("/api/v1/comments/{commentId}", replyId)
                            .with(jwt().jwt(token -> token.subject(replier.getId().toString()))))
                    .andExpect(status().isNoContent());

            Boolean deleted = jdbcTemplate.queryForObject(
                    "select deleted_at is not null from comments where id = ?",
                    Boolean.class,
                    replyId);
            Long storedParent = jdbcTemplate.queryForObject(
                    "select parent_comment_id from comments where id = ?",
                    Long.class,
                    replyId);
            assertThat(deleted).isTrue();
            assertThat(storedParent).isEqualTo(parentId);
            assertThat(itemOrNull(readComments(firstPost), replyId)).isNull();
        } finally {
            jdbcTemplate.update("update comments set deleted_at = null where id = ?", replyId);
        }
    }

    private JsonNode createComment(
            UserAccount user,
            JourneyPost post,
            String content,
            Long parentCommentId,
            boolean includeParentField)
            throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("content", content);
        if (includeParentField) {
            request.put("parentCommentId", parentCommentId);
        }
        MvcResult result = mockMvc.perform(post("/api/v1/posts/{postId}/comments", post.getId())
                        .with(jwt().jwt(token -> token.subject(user.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private void createCommentExpectBadRequest(
            UserAccount user, JourneyPost post, String content, Long parentCommentId)
            throws Exception {
        mockMvc.perform(post("/api/v1/posts/{postId}/comments", post.getId())
                        .with(jwt().jwt(token -> token.subject(user.getId().toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "content", content,
                                "parentCommentId", parentCommentId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMENT_PARENT_INVALID"));
    }

    private JsonNode readComments(JourneyPost post) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/posts/{postId}/comments", post.getId()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private JsonNode item(JsonNode page, long id) {
        JsonNode found = itemOrNull(page, id);
        assertThat(found).as("comment %s in flat page", id).isNotNull();
        return found;
    }

    private JsonNode itemOrNull(JsonNode page, long id) {
        for (JsonNode item : page.path("items")) {
            if (item.path("id").asLong() == id) {
                return item;
            }
        }
        return null;
    }
}
