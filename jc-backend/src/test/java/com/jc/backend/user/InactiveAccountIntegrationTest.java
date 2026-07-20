package com.jc.backend.user;

import static com.jc.backend.CanonicalTestData.region;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jc.backend.CanonicalPostgresTest;
import com.jc.backend.common.DomainException;
import com.jc.backend.crew.CrewDtos;
import com.jc.backend.crew.CrewService;
import com.jc.backend.post.JourneyPost;
import com.jc.backend.post.JourneyPostRepository;
import com.jc.backend.post.PostService;
import com.jc.backend.post.PostStatus;
import com.jc.backend.region.Region;
import com.jc.backend.region.RegionRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

@CanonicalPostgresTest
class InactiveAccountIntegrationTest {

    @Autowired private UserRepository users;
    @Autowired private RegionRepository regions;
    @Autowired private JourneyPostRepository posts;
    @Autowired private UserService userService;
    @Autowired private PostService postService;
    @Autowired private CrewService crewService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    private UserAccount user;
    private JourneyPost draft;

    @BeforeEach
    void setUp() {
        user = users.saveAndFlush(new UserAccount(
                "inactive-user@example.com", "hash", "inactive-user"));
        Region seoul = region(regions, "KR-SEOUL");
        draft = posts.saveAndFlush(new JourneyPost(
                user, seoul, "inactive draft", "content", PostStatus.DRAFT));
        jdbcTemplate.update(
                "update public.app_users set account_status = 'suspended' where id = ?",
                user.getId());
        entityManager.clear();
    }

    @Test
    void suspendedUserCannotUseAuthenticatedProfilePostOrCrewMutations() {
        assertInactive(() -> userService.me(user.getId()));
        assertInactive(() -> postService.myPosts(user.getId(), PageRequest.of(0, 20)));
        assertInactive(() -> postService.myBookmarks(user.getId(), PageRequest.of(0, 20)));
        assertInactive(() -> postService.delete(user.getId(), draft.getId()));
        assertInactive(() -> postService.unlike(user.getId(), draft.getId()));
        assertInactive(() -> postService.unbookmark(user.getId(), draft.getId()));
        assertInactive(() -> postService.deleteComment(user.getId(), 1L));
        assertInactive(() -> crewService.cancelJoin(user.getId(), 1L));
        assertInactive(() -> crewService.applications(
                user.getId(), 1L, PageRequest.of(0, 20)));
        assertInactive(() -> crewService.review(
                user.getId(),
                1L,
                1L,
                new CrewDtos.ReviewRequest(com.jc.backend.crew.CrewMemberStatus.APPROVED)));
        assertInactive(() -> crewService.create(
                user.getId(),
                new CrewDtos.CreateRequest(
                        "inactive crew",
                        "KR-SEOUL",
                        null,
                        "must be rejected",
                        LocalDate.now().plusDays(10),
                        4,
                        true)));
    }

    private void assertInactive(Runnable operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(DomainException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("USER_INACTIVE"));
    }
}
