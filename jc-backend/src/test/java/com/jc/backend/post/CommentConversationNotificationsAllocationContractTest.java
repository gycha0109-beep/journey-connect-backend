package com.jc.backend.post;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CommentConversationNotificationsAllocationContractTest {

    private static final String DOCUMENT =
            "docs/platform/governance/SC-PF8-COMMENT-CONVERSATION-NOTIFICATIONS-ALLOCATION.md";
    private static final String AMENDMENT =
            "docs/platform/governance/SC-PF8-COMMENT-CONVERSATION-NOTIFICATIONS-SQL-AMENDMENT.md";

    @Test
    void allocationLocksRecipientsDedupeTransactionAndSql67To68Boundary() throws IOException {
        String allocation = read(DOCUMENT);

        for (String required : new String[] {
                "sc-pf8-comment-conversation-notifications-v1",
                "AMENDED / IMPLEMENTATION_AUTHORITY_GRANTED",
                "CONTRACT_ID=comment-conversation-notification-v1",
                "TOP_LEVEL_COMMENT_EVENT=post_comment",
                "TOP_LEVEL_COMMENT_RECIPIENT=post.author_id",
                "REPLY_EVENT=comment_reply",
                "REPLY_RECIPIENT=parent_comment.author_id",
                "SELF_NOTIFICATION=SUPPRESS",
                "TOP_LEVEL_DEDUPE_KEY=post_comment:{commentId}",
                "REPLY_DEDUPE_KEY=comment_reply:{replyCommentId}",
                "COMMENT_NOTIFICATION_CONSISTENCY=SAME_APP_TRANSACTION",
                "67_comment_conversation_notification_types.sql",
                "68_comment_conversation_notification_types_smoke_test.sql",
                "SQL_69_PLUS=UNALLOCATED",
                "public.user_notifications",
                "One successful comment write produces at most one PF8 notification event"
        }) {
            assertTrue(allocation.contains(required), "PF8 allocation missing: " + required);
        }
    }

    @Test
    void sqlAmendmentIsExplicitAndDoesNotWidenStorageOrRoles() throws IOException {
        String allocation = read(DOCUMENT);
        String amendment = read(AMENDMENT);

        assertTrue(amendment.contains("sc-pf8-comment-notification-sql-amendment-v1"));
        assertTrue(amendment.contains("canonical SQL55"));
        assertTrue(amendment.contains("user_notifications_type_check"));
        assertTrue(amendment.contains("user_notifications_target_type_check"));
        assertTrue(amendment.contains("SQL67/68"));
        assertTrue(amendment.contains("SQL69+ remains unallocated"));
        assertTrue(allocation.contains("do not alter columns, indexes, sequence ownership, unique dedupe semantics, RLS, roles, or grants"));
        assertTrue(allocation.contains("reject mismatched type/target pairs"));
    }

    @Test
    void allocationDoesNotGrantLikeReportRealtimeRecommendationSearchOrSql69() throws IOException {
        String allocation = read(DOCUMENT);

        for (String prohibitedAuthority : new String[] {
                "post like notification",
                "report-created or report-result notification",
                "WebSocket/SSE",
                "Recommendation/Search feedback",
                "SQL `69+`"
        }) {
            assertTrue(allocation.contains(prohibitedAuthority),
                    "PF8 non-goal must remain explicit: " + prohibitedAuthority);
        }

        assertFalse(allocation.contains("POST_LIKE_NOTIFICATION=YES"));
        assertFalse(allocation.contains("REPORT_NOTIFICATION=YES"));
        assertFalse(allocation.contains("RECOMMENDATION_FEEDBACK=YES"));
        assertFalse(allocation.contains("SEARCH_SIGNAL=YES"));
        assertFalse(allocation.contains("SQL_69_PLUS=ALLOCATED"));
    }

    @Test
    void runtimeAllocationIsNarrow() throws IOException {
        String allocation = read(DOCUMENT);

        assertTrue(allocation.contains(
                "jc-backend/src/main/java/com/jc/backend/notification/NotificationService.java"));
        assertTrue(allocation.contains(
                "jc-backend/src/main/java/com/jc/backend/post/CommentReplyService.java"));
        assertTrue(allocation.contains(
                "Changes to `PostController`, `PostDtos`, `RecommendationPostInteractionService`"));
        assertTrue(allocation.contains("no DB grant expansion"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(repositoryRoot().resolve(relativePath))
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        for (Path candidate = current; candidate != null; candidate = candidate.getParent()) {
            if (Files.isDirectory(candidate.resolve("docs/platform/governance"))
                    && Files.isRegularFile(candidate.resolve("jc-backend/build.gradle.kts"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("repository root not found from " + current);
    }
}
