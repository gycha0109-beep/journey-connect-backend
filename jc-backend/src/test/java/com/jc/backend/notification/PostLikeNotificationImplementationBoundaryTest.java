package com.jc.backend.notification;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PostLikeNotificationImplementationBoundaryTest {

    @Test
    void canonicalSql69And70CopiesRemainByteIdentical() throws IOException {
        assertByteIdentical("69_post_like_notification_type.sql");
        assertByteIdentical("70_post_like_notification_type_smoke_test.sql");
    }

    @Test
    void sql69OnlyWidensExistingNotificationCheckDomain() throws IOException {
        String sql = read("database/journey-connect-db-v2.7/69_post_like_notification_type.sql");

        for (String required : new String[] {
                "DROP CONSTRAINT user_notifications_type_check",
                "DROP CONSTRAINT user_notifications_target_type_check",
                "'crew_application'",
                "'crew_approved'",
                "'crew_rejected'",
                "'post_comment'",
                "'comment_reply'",
                "'post_like'",
                "target_type = 'crew'",
                "target_type = 'post'"
        }) {
            assertTrue(sql.contains(required), "PF10 SQL69 contract missing: " + required);
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
            assertFalse(sql.contains(prohibited), "PF10 SQL69 widened schema/privilege surface: " + prohibited);
        }
    }

    @Test
    void runtimeUsesCanonicalTransitionResultAndOneOuterAppTransaction() throws IOException {
        String interactionService = read(
                "jc-backend/src/main/java/com/jc/backend/recommendation/application/RecommendationPostInteractionService.java");
        String coordinator = read(
                "jc-backend/src/main/java/com/jc/backend/post/PostLikeNotificationCoordinator.java");
        String notifications = read(
                "jc-backend/src/main/java/com/jc/backend/notification/NotificationService.java");
        String controller = read(
                "jc-backend/src/main/java/com/jc/backend/post/PostController.java");

        assertTrue(interactionService.contains("public Result applyWithResult("));
        assertTrue(interactionService.contains("applyWithResult(userId, tokenId, postId, action, tracking);"));
        assertTrue(interactionService.contains("Result result = interactionStore.apply("));
        assertTrue(interactionService.contains("return result;"));

        for (String required : new String[] {
                "@DatabaseTransactional(role = DatabaseRole.APP)",
                "Action.LIKE",
                "if (result == Result.APPLIED)",
                "notifications.postLiked(userId, postId);"
        }) {
            assertTrue(coordinator.contains(required), "PF10 coordinator contract missing: " + required);
        }

        for (String required : new String[] {
                "postLiked(long actorId, long postId)",
                "insert into public.user_notifications",
                "select p.author_id",
                "'post_like'",
                "and p.author_id <> ?",
                "post_like:\" + postId + \":\" + actorId",
                "on conflict (dedupe_key) do nothing"
        }) {
            assertTrue(notifications.contains(required), "PF10 notification producer missing: " + required);
        }

        assertTrue(controller.contains("postLikeNotificationCoordinator.like("));
        assertTrue(controller.contains("Action.UNLIKE"));
        assertTrue(controller.contains("Action.SAVE"));
        assertTrue(controller.contains("Action.UNSAVE"));

        String pf10Runtime = coordinator + notifications;
        for (String prohibited : new String[] {
                "insert into public.post_likes",
                "delete from public.post_likes",
                "apply_recommendation_post_interaction(",
                "recommendation_behavior_event",
                "@Async",
                "afterCommit",
                "TransactionSynchronization",
                "WebSocket",
                "SseEmitter"
        }) {
            assertFalse(pf10Runtime.contains(prohibited), "PF10 created a parallel/asynchronous authority: " + prohibited);
        }
    }

    @Test
    void bootstrapStopsAtSql70AndSql71RemainsAbsent() throws IOException {
        String initializer = read("jc-backend/src/test/java/com/jc/backend/CanonicalPostgresInitializer.java");
        assertTrue(initializer.contains("69_post_like_notification_type.sql"));
        assertTrue(initializer.contains("70_post_like_notification_type_smoke_test.sql"));

        Path production = repositoryRoot().resolve("database/journey-connect-db-v2.7");
        Path mirror = repositoryRoot().resolve("jc-backend/src/test/resources/db/canonical");
        try (var files = Files.list(production)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().matches("^71_.*\\.sql$")));
        }
        try (var files = Files.list(mirror)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().matches("^71_.*\\.sql$")));
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
