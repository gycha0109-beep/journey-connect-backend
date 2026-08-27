package com.jc.backend.post;

import static com.jc.backend.CanonicalTestData.publishedPost;
import static com.jc.backend.CanonicalTestData.region;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jc.backend.CanonicalPostgresTest;
import com.jc.backend.common.DomainException;
import com.jc.backend.notification.NotificationDtos;
import com.jc.backend.notification.NotificationService;
import com.jc.backend.region.Region;
import com.jc.backend.region.RegionRepository;
import com.jc.backend.user.UserAccount;
import com.jc.backend.user.UserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

@CanonicalPostgresTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CommentConversationNotificationsIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository users;
    @Autowired private RegionRepository regions;
    @Autowired private JourneyPostRepository posts;
    @Autowired private PlaceRepository places;
    @Autowired private CommentReplyService commentReplies;
    @Autowired private NotificationService notifications;

    private UserAccount postOwner;
    private UserAccount parentAuthor;
    private UserAccount replier;
    private JourneyPost firstPost;
    private JourneyPost secondPost;

    @BeforeEach
    void setUp() {
        postOwner = users.save(new UserAccount("pf8-owner@example.com", "hash", "pf8-owner"));
        parentAuthor = users.save(new UserAccount("pf8-parent@example.com", "hash", "pf8-parent"));
        replier = users.save(new UserAccount("pf8-replier@example.com", "hash", "pf8-replier"));
        Region seoul = region(regions, "KR-SEOUL");
        firstPost = posts.save(publishedPost(places, postOwner, seoul, "PF8 first", "first"));
        secondPost = posts.save(publishedPost(places, postOwner, seoul, "PF8 second", "second"));
    }

    @Test
    void topLevelCommentNotifiesPostAuthorExactlyOnceAndSelfCommentIsSuppressed() {
        PostDtos.CommentView comment = commentReplies.addComment(
                parentAuthor.getId(), firstPost.getId(), "top-level", null);

        List<NotificationDtos.Item> ownerItems = notifications.list(postOwner.getId(), 0, 20).items();
        assertThat(ownerItems).hasSize(1);
        NotificationDtos.Item notification = ownerItems.get(0);
        assertThat(notification.type()).isEqualTo("post_comment");
        assertThat(notification.targetType()).isEqualTo("post");
        assertThat(notification.targetId()).isEqualTo(firstPost.getId());
        assertThat(notification.actor().id()).isEqualTo(parentAuthor.getId());
        assertThat(notification.read()).isFalse();
        assertThat(notifications.unreadCount(postOwner.getId()).count()).isEqualTo(1L);

        String dedupeKey = jdbcTemplate.queryForObject(
                "select dedupe_key from public.user_notifications where id = ?",
                String.class,
                notification.id());
        assertThat(dedupeKey).isEqualTo("post_comment:" + comment.id());

        commentReplies.addComment(postOwner.getId(), firstPost.getId(), "self-comment", null);
        assertThat(notifications.list(postOwner.getId(), 0, 20).items()).hasSize(1);
    }

    @Test
    void replyNotifiesParentAuthorOnlyWithoutSecondPostAuthorNotification() {
        PostDtos.CommentView parent = commentReplies.addComment(
                parentAuthor.getId(), firstPost.getId(), "parent", null);
        assertThat(notifications.list(postOwner.getId(), 0, 20).items()).hasSize(1);

        PostDtos.CommentView reply = commentReplies.addComment(
                replier.getId(), firstPost.getId(), "reply", parent.id());

        List<NotificationDtos.Item> parentItems = notifications.list(parentAuthor.getId(), 0, 20).items();
        assertThat(parentItems).hasSize(1);
        NotificationDtos.Item replyNotification = parentItems.get(0);
        assertThat(replyNotification.type()).isEqualTo("comment_reply");
        assertThat(replyNotification.targetType()).isEqualTo("post");
        assertThat(replyNotification.targetId()).isEqualTo(firstPost.getId());
        assertThat(replyNotification.actor().id()).isEqualTo(replier.getId());

        String dedupeKey = jdbcTemplate.queryForObject(
                "select dedupe_key from public.user_notifications where id = ?",
                String.class,
                replyNotification.id());
        assertThat(dedupeKey).isEqualTo("comment_reply:" + reply.id());

        List<NotificationDtos.Item> ownerItems = notifications.list(postOwner.getId(), 0, 20).items();
        assertThat(ownerItems).hasSize(1);
        assertThat(ownerItems.get(0).type()).isEqualTo("post_comment");

        commentReplies.addComment(parentAuthor.getId(), firstPost.getId(), "self-reply", parent.id());
        assertThat(notifications.list(parentAuthor.getId(), 0, 20).items()).hasSize(1);
    }

    @Test
    void invalidParentCreatesNeitherReplyNorNotification() {
        PostDtos.CommentView otherPostParent = commentReplies.addComment(
                parentAuthor.getId(), secondPost.getId(), "other-post-parent", null);
        long beforeCommentCount = count("select count(*) from public.comments");
        long beforeNotificationCount = count("select count(*) from public.user_notifications");

        assertThatThrownBy(() -> commentReplies.addComment(
                        replier.getId(), firstPost.getId(), "cross-post-invalid", otherPostParent.id()))
                .isInstanceOfSatisfying(DomainException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("COMMENT_PARENT_INVALID"));
        assertThatThrownBy(() -> commentReplies.addComment(
                        replier.getId(), firstPost.getId(), "missing-invalid", Long.MAX_VALUE))
                .isInstanceOfSatisfying(DomainException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("COMMENT_PARENT_INVALID"));

        assertThat(count("select count(*) from public.comments")).isEqualTo(beforeCommentCount);
        assertThat(count("select count(*) from public.user_notifications")).isEqualTo(beforeNotificationCount);
    }

    @Test
    void notificationFailureRollsBackCommentWriteInSameAppTransaction() {
        jdbcTemplate.execute("""
                alter table public.user_notifications
                add constraint pf8_test_reject_post_notification
                check (type not in ('post_comment', 'comment_reply'))
                """);
        try {
            assertThatThrownBy(() -> commentReplies.addComment(
                            parentAuthor.getId(), firstPost.getId(), "rollback-probe", null))
                    .isInstanceOf(DataIntegrityViolationException.class);

            Long comments = jdbcTemplate.queryForObject(
                    "select count(*) from public.comments where content = 'rollback-probe'",
                    Long.class);
            Long notificationRows = jdbcTemplate.queryForObject(
                    "select count(*) from public.user_notifications where type = 'post_comment'",
                    Long.class);
            assertThat(comments).isZero();
            assertThat(notificationRows).isZero();
        } finally {
            jdbcTemplate.execute("""
                    alter table public.user_notifications
                    drop constraint if exists pf8_test_reject_post_notification
                    """);
        }
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }
}
