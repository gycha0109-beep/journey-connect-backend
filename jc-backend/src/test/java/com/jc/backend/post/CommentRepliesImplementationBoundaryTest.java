package com.jc.backend.post;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CommentRepliesImplementationBoundaryTest {

    @Test
    void canonicalSql65And66CopiesRemainByteIdentical() throws IOException {
        assertByteIdentical("65_comment_replies.sql");
        assertByteIdentical("66_comment_replies_smoke_test.sql");
    }

    @Test
    void runtimeKeepsOneDepthVisibleParentAndAppRoleBoundary() throws IOException {
        String service = read("jc-backend/src/main/java/com/jc/backend/post/CommentReplyService.java");
        String repository = read("jc-backend/src/main/java/com/jc/backend/post/CommentRepository.java");

        for (String required : new String[] {
                "@DatabaseTransactional(role = DatabaseRole.APP",
                "findVisibleTopLevelParent(parentCommentId, postId)",
                "comment.getParentCommentId()"
        }) {
            assertTrue(service.contains(required), "PF7 runtime boundary missing: " + required);
        }
        for (String required : new String[] {
                "c.post.id = :postId",
                "c.parent is null",
                "c.deletedAt is null",
                "c.moderationDeletedAt is null",
                "c.author.accountStatus = 'active'"
        }) {
            assertTrue(repository.contains(required), "PF7 parent validity query missing: " + required);
        }

        String runtime = service + repository;
        assertFalse(runtime.contains("Recommendation"));
        assertFalse(runtime.contains("Exposure"));
        assertFalse(runtime.contains("Search"));
        assertFalse(runtime.contains("Notification"));
    }

    @Test
    void sql65IsSingleTableStructuralExtensionOnly() throws IOException {
        String sql = read("database/journey-connect-db-v2.7/65_comment_replies.sql");

        assertTrue(sql.contains("ADD COLUMN parent_comment_id bigint"));
        assertTrue(sql.contains("REFERENCES public.comments(id)"));
        assertTrue(sql.contains("comments_enforce_reply_structure"));
        assertTrue(sql.contains("v_parent_post_id IS DISTINCT FROM NEW.post_id"));
        assertTrue(sql.contains("v_parent_parent_comment_id IS NOT NULL"));
        assertTrue(sql.contains("GRANT INSERT (parent_comment_id) ON public.comments TO jc_app"));
        assertTrue(sql.contains("REVOKE UPDATE (parent_comment_id) ON public.comments FROM jc_app"));

        assertFalse(sql.contains("CREATE TABLE"));
        assertFalse(sql.contains("notification"));
        assertFalse(sql.contains("recommendation"));
        assertFalse(sql.contains("exposure"));
        assertFalse(sql.contains("search"));
    }

    @Test
    void apiContractRemainsFlatAndBackwardCompatible() throws IOException {
        String dtos = read("jc-backend/src/main/java/com/jc/backend/post/PostDtos.java");
        String controller = read("jc-backend/src/main/java/com/jc/backend/post/PostController.java");

        assertTrue(dtos.contains("public CommentRequest(String content)"));
        assertTrue(dtos.contains("this(content, null);"));
        assertTrue(dtos.contains("Long parentCommentId"));
        assertTrue(controller.contains("@GetMapping(\"/posts/{postId}/comments\")"));
        assertTrue(controller.contains("@PostMapping(\"/posts/{postId}/comments\")"));
        assertTrue(controller.contains("@DeleteMapping(\"/comments/{commentId}\")"));
        assertTrue(controller.contains("request.content(), request.parentCommentId()"));
        assertFalse(dtos.contains("List<CommentView> replies"));
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
