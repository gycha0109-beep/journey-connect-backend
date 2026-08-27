package com.jc.backend.post;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CommentConversationNotificationsImplementationBoundaryTest {

    @Test
    void canonicalSql67And68CopiesRemainByteIdentical() throws IOException {
        assertByteIdentical("67_comment_conversation_notification_types.sql");
        assertByteIdentical("68_comment_conversation_notification_types_smoke_test.sql");
    }

    @Test
    void sql67OnlyWidensExistingNotificationConstraintDomain() throws IOException {
        String sql = read("database/journey-connect-db-v2.7/67_comment_conversation_notification_types.sql");

        for (String required : new String[] {
                "DROP CONSTRAINT user_notifications_type_check",
                "DROP CONSTRAINT user_notifications_target_type_check",
                "'crew_application'",
                "'crew_approved'",
                "'crew_rejected'",
                "'post_comment'",
                "'comment_reply'",
                "target_type = 'crew'",
                "target_type = 'post'"
        }) {
            assertTrue(sql.contains(required), "PF8 SQL67 contract missing: " + required);
        }

        for (String prohibited : new String[] {
                "CREATE TABLE",
                "ADD COLUMN",
                "CREATE INDEX",
                "CREATE SEQUENCE",
                "CREATE FUNCTION",
                "CREATE PROCEDURE",
                "CREATE TRIGGER",
                "GRANT ",
                "REVOKE "
        }) {
            assertFalse(sql.contains(prohibited), "PF8 SQL67 widened schema/privilege surface: " + prohibited);
        }
    }

    @Test
    void runtimeUsesSingleAppTransactionAndExistingInboxOnly() throws IOException {
        String notificationService = read(
                "jc-backend/src/main/java/com/jc/backend/notification/NotificationService.java");
        String commentService = read(
                "jc-backend/src/main/java/com/jc/backend/post/CommentReplyService.java");
        String runtime = notificationService + commentService;

        for (String required : new String[] {
                "@DatabaseTransactional(role = DatabaseRole.APP",
                "postCommented(long actorId, long recipientId, long postId, long commentId)",
                "commentReplied(long actorId, long recipientId, long postId, long replyCommentId)",
                "insert into public.user_notifications",
                "on conflict (dedupe_key) do nothing",
                "post_comment:\" + commentId",
                "comment_reply:\" + replyCommentId",
                "if (actorId == recipientId)",
                "notifications.postCommented(",
                "notifications.commentReplied("
        }) {
            assertTrue(runtime.contains(required), "PF8 runtime contract missing: " + required);
        }

        assertTrue(commentService.contains("if (parent == null)"));
        assertTrue(commentService.contains("} else {"));
        assertFalse(runtime.contains("RecommendationPostInteractionService"));
        assertFalse(runtime.contains("recommendation_behavior_event"));
        assertFalse(runtime.contains("Search"));
        assertFalse(runtime.contains("Exposure"));
        assertFalse(runtime.contains("WebSocket"));
        assertFalse(runtime.contains("SseEmitter"));
    }

    @Test
    void canonicalBootstrapIncludesPf8SqlAndSql69RemainsAbsent() throws IOException {
        String initializer = read("jc-backend/src/test/java/com/jc/backend/CanonicalPostgresInitializer.java");
        assertTrue(initializer.contains("67_comment_conversation_notification_types.sql"));
        assertTrue(initializer.contains("68_comment_conversation_notification_types_smoke_test.sql"));

        Path production = repositoryRoot().resolve("database/journey-connect-db-v2.7");
        Path mirror = repositoryRoot().resolve("jc-backend/src/test/resources/db/canonical");
        assertFalse(Files.exists(production.resolve("69_comment_conversation_notifications.sql")));
        assertFalse(Files.exists(mirror.resolve("69_comment_conversation_notifications.sql")));
        try (var files = Files.list(production)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().matches("^69_.*\\.sql$")));
        }
        try (var files = Files.list(mirror)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().matches("^69_.*\\.sql$")));
        }
    }

    private void assertByteIdentical(String fileName) throws IOException {
        byte[] canonical = Files.readAllBytes(repositoryRoot()
                .resolve("database/journey-connect-db-v2.7")
                .resolve(fileName));
        byte[] mirror = Files.readAllBytes(repositoryRoot()
                .resolve("jc-backend/src/test/resources/db/canonical")
                .resolve(fileName));
        assertTrue(java.util.Arrays.equals(canonical, mirror), fileName + " canonical/test mirror drift");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(repositoryRoot().resolve(relativePath))
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }

    private Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        for (Path candidate = current; candidate != null; candidate = candidate.getParent()) {
            if (Files.isDirectory(candidate.resolve("database/journey-connect-db-v2.7"))
                    && Files.isRegularFile(candidate.resolve("jc-backend/build.gradle.kts"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("repository root not found from " + current);
    }
}
