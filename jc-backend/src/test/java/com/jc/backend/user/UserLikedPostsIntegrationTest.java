package com.jc.backend.user;

import static com.jc.backend.CanonicalTestData.publishedPost;
import static com.jc.backend.CanonicalTestData.region;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jc.backend.CanonicalPostgresTest;
import com.jc.backend.post.JourneyPost;
import com.jc.backend.post.JourneyPostRepository;
import com.jc.backend.post.PlaceRepository;
import com.jc.backend.region.Region;
import com.jc.backend.region.RegionRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@CanonicalPostgresTest
@AutoConfigureMockMvc
class UserLikedPostsIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private RegionRepository regions;
    @Autowired private JourneyPostRepository posts;
    @Autowired private PlaceRepository places;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void myLikesUsesCurrentPublicVisibilityAndLatestLikeOrdering() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UserAccount viewer = users.save(new UserAccount(
                "liked-post-viewer-" + suffix + "@example.com",
                "hash",
                "liked-viewer-" + suffix));
        UserAccount activeAuthor = users.save(new UserAccount(
                "liked-post-author-" + suffix + "@example.com",
                "hash",
                "liked-author-" + suffix));
        UserAccount inactiveAuthor = users.save(new UserAccount(
                "liked-post-inactive-author-" + suffix + "@example.com",
                "hash",
                "liked-inactive-" + suffix));
        Region seoul = region(regions, "KR-SEOUL");

        JourneyPost visibleOld = posts.save(publishedPost(
                places, activeAuthor, seoul, "liked-visible-old-" + suffix, "content"));
        JourneyPost hidden = posts.save(publishedPost(
                places, activeAuthor, seoul, "liked-hidden-" + suffix, "content"));
        JourneyPost visibleNew = posts.save(publishedPost(
                places, activeAuthor, seoul, "liked-visible-new-" + suffix, "content"));
        JourneyPost inactiveAuthorPost = posts.save(publishedPost(
                places, inactiveAuthor, seoul, "liked-inactive-author-" + suffix, "content"));
        JourneyPost privatePost = posts.save(publishedPost(
                places, activeAuthor, seoul, "liked-private-" + suffix, "content"));

        like(viewer, visibleOld);
        like(viewer, hidden);
        like(viewer, visibleNew);
        like(viewer, inactiveAuthorPost);
        like(viewer, privatePost);

        jdbc.update(
                "update public.posts set moderation_status = 'hidden' where id = ?",
                hidden.getId());
        jdbc.update(
                "update public.app_users set account_status = 'suspended' where id = ?",
                inactiveAuthor.getId());
        jdbc.update(
                "update public.posts set visibility = 'private' where id = ?",
                privatePost.getId());

        mockMvc.perform(get("/api/v1/users/me/likes")
                        .param("page", "0")
                        .param("size", "1")
                        .with(jwt().jwt(token -> token.subject(viewer.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(visibleNew.getId()))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.last").value(false));

        mockMvc.perform(get("/api/v1/users/me/likes")
                        .param("page", "1")
                        .param("size", "1")
                        .with(jwt().jwt(token -> token.subject(viewer.getId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(visibleOld.getId()))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.last").value(true));
    }

    @Test
    void myLikesRequiresAuthenticationAndActiveRequester() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/likes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UserAccount inactive = users.save(new UserAccount(
                "liked-post-inactive-viewer-" + suffix + "@example.com",
                "hash",
                "liked-inactive-viewer-" + suffix));
        jdbc.update(
                "update public.app_users set account_status = 'suspended' where id = ?",
                inactive.getId());

        mockMvc.perform(get("/api/v1/users/me/likes")
                        .with(jwt().jwt(token -> token.subject(inactive.getId().toString()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_INACTIVE"));
    }

    private void like(UserAccount viewer, JourneyPost post) throws Exception {
        mockMvc.perform(post("/api/v1/posts/{postId}/likes", post.getId())
                        .with(jwt().jwt(token -> token.subject(viewer.getId().toString()))))
                .andExpect(status().isNoContent());
    }
}
