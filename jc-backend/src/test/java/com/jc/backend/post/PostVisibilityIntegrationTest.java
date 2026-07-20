package com.jc.backend.post;

import com.jc.backend.CanonicalPostgresTest;
import static com.jc.backend.CanonicalTestData.publishedPost;
import static com.jc.backend.CanonicalTestData.region;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jc.backend.common.DomainException;
import com.jc.backend.database.DatabaseRequestIdentity;
import com.jc.backend.common.PageResponse;
import com.jc.backend.region.Region;
import com.jc.backend.region.RegionRepository;
import com.jc.backend.user.UserAccount;
import com.jc.backend.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

@CanonicalPostgresTest
class PostVisibilityIntegrationTest {

    @Autowired private UserRepository users;
    @Autowired private RegionRepository regions;
    @Autowired private JourneyPostRepository posts;
    @Autowired private PlaceRepository places;
    @Autowired private PostService postService;
    @Autowired private PostAccessPolicy accessPolicy;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;
    @Autowired private DatabaseRequestIdentity requestIdentity;

    private UserAccount owner;
    private UserAccount other;
    private JourneyPost draft;

    @BeforeEach
    void setUp() {
        owner = users.save(new UserAccount("owner@example.com", "hash", "owner"));
        other = users.save(new UserAccount("other@example.com", "hash", "other"));
        Region seoul = region(regions, "KR-SEOUL");

        draft = posts.saveAndFlush(new JourneyPost(
                owner, seoul, "draft", "private", PostStatus.DRAFT));
        posts.saveAndFlush(publishedPost(places, owner, seoul, "public", "visible"));
    }

    @Test
    void anonymousAndOtherUserCannotReadDraft() {
        assertThatThrownBy(() -> postService.detail(draft.getId(), null))
                .isInstanceOfSatisfying(DomainException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getCode()).isEqualTo("POST_NOT_FOUND");
                });

        assertThatThrownBy(() -> postService.detail(draft.getId(), other.getId()))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void authorCanReadDraft() {
        PostDtos.Detail detail = postService.detail(draft.getId(), owner.getId());
        assertThat(detail.id()).isEqualTo(draft.getId());
        assertThat(detail.title()).isEqualTo("draft");
    }

    @Test
    void publicProfileExcludesDraftButMyPostsIncludesIt() {
        PageResponse<PostDtos.Summary> publicPosts =
                postService.publicUserPosts(owner.getId(), PageRequest.of(0, 20));
        PageResponse<PostDtos.Summary> myPosts =
                postService.myPosts(owner.getId(), PageRequest.of(0, 20));

        assertThat(publicPosts.items()).extracting(PostDtos.Summary::title)
                .containsExactly("public");
        assertThat(myPosts.items()).extracting(PostDtos.Summary::title)
                .containsExactlyInAnyOrder("public", "draft");
    }

    @Test
    void anonymousCannotReadDraftComments() {
        assertThatThrownBy(() ->
                postService.comments(draft.getId(), null, PageRequest.of(0, 20)))
                .isInstanceOf(DomainException.class);
    }
    @Test
    void followersVisibilityUsesCanonicalFollowPolicy() {
        JourneyPost followersPost = posts.findByAuthorIdAndPublishedTrueOrderByCreatedAtDescIdDesc(
                        owner.getId(), PageRequest.of(0, 20))
                .getContent()
                .stream()
                .filter(post -> post.getTitle().equals("public"))
                .findFirst()
                .orElseThrow();
        jdbcTemplate.update(
                "update public.posts set visibility = 'followers' where id = ?",
                followersPost.getId());
        entityManager.clear();

        assertThat(accessPolicy.canView(other.getId(), followersPost.getId())).isFalse();

        jdbcTemplate.update(
                "insert into public.follows (follower_id, following_id) values (?, ?)",
                other.getId(),
                owner.getId());

        try (DatabaseRequestIdentity.Scope ignored = requestIdentity.open(other.getId())) {
            assertThat(postService.detail(followersPost.getId(), other.getId()).id())
                    .isEqualTo(followersPost.getId());
        }
    }

    @Test
    void bookmarkedFollowersPostDisappearsAfterFollowIsRevoked() {
        JourneyPost followersPost = posts.findByAuthorIdAndPublishedTrueOrderByCreatedAtDescIdDesc(
                        owner.getId(), PageRequest.of(0, 20))
                .getContent()
                .stream()
                .filter(post -> post.getTitle().equals("public"))
                .findFirst()
                .orElseThrow();
        jdbcTemplate.update(
                "update public.posts set visibility = 'followers' where id = ?",
                followersPost.getId());
        jdbcTemplate.update(
                "insert into public.follows (follower_id, following_id) values (?, ?)",
                other.getId(),
                owner.getId());
        jdbcTemplate.update(
                "insert into public.bookmarks (post_id, user_id) values (?, ?)",
                followersPost.getId(),
                other.getId());

        assertThat(postService.myBookmarks(other.getId(), PageRequest.of(0, 20)).items())
                .extracting(PostDtos.Summary::id)
                .containsExactly(followersPost.getId());

        jdbcTemplate.update(
                "delete from public.follows where follower_id = ? and following_id = ?",
                other.getId(),
                owner.getId());
        entityManager.clear();

        assertThat(postService.myBookmarks(other.getId(), PageRequest.of(0, 20)).items())
                .isEmpty();
    }

}
