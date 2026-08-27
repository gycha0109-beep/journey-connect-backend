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

    @Test
    void allocationLocksRecipientsDedupeTransactionAndNoSqlBoundary() throws IOException {
        String allocation = read(DOCUMENT);

        for (String required : new String[] {
                "sc-pf8-comment-conversation-notifications-v1",
                "APPROVED / IMPLEMENTATION_AUTHORITY_GRANTED",
                "CONTRACT_ID=comment-conversation-notification-v1",
                "TOP_LEVEL_COMMENT_EVENT=post_comment",
                "TOP_LEVEL_COMMENT_RECIPIENT=post.author_id",
                "REPLY_EVENT=comment_reply",
                "REPLY_RECIPIENT=parent_comment.author_id",
                "SELF_NOTIFICATION=SUPPRESS",
                "TOP_LEVEL_DEDUPE_KEY=post_comment:{commentId}",
                "REPLY_DEDUPE_KEY=comment_reply:{replyCommentId}",
                "COMMENT_NOTIFICATION_CONSISTENCY=SAME_APP_TRANSACTION",
                "NEW_SQL=NONE",
                "SQL_67_PLUS=UNALLOCATED",
                "public.user_notifications",
                "One successful comment write produces at most one PF8 notification event"
        }) {
            assertTrue(allocation.contains(required), "PF8 allocation missing: " + required);
        }
    }

    @Test
    void allocationDoesNotGrantLikeReportRealtimeRecommendationSearchOrSql67() throws IOException {
        String allocation = read(DOCUMENT);

        for (String prohibitedAuthority : new String[] {
                "post like notification",
                "report-created or report-result notification",
                "WebSocket/SSE",
                "Recommendation/Search feedback",
                "SQL `67+`"
        }) {
            assertTrue(allocation.contains(prohibitedAuthority),
                    "PF8 non-goal must remain explicit: " + prohibitedAuthority);
        }

        assertFalse(allocation.contains("POST_LIKE_NOTIFICATION=YES"));
        assertFalse(allocation.contains("REPORT_NOTIFICATION=YES"));
        assertFalse(allocation.contains("RECOMMENDATION_FEEDBACK=YES"));
        assertFalse(allocation.contains("SEARCH_SIGNAL=YES"));
        assertFalse(allocation.contains("SQL_67_PLUS=ALLOCATED"));
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
        assertTrue(allocation.contains("no role grant widening is permitted"));
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
