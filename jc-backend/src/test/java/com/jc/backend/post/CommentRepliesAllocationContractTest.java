package com.jc.backend.post;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CommentRepliesAllocationContractTest {

    private static final String DOCUMENT =
            "docs/platform/governance/SC-PF7-COMMENT-REPLIES-ALLOCATION.md";

    @Test
    void allocationLocksOneDepthVisibilityAndSqlBoundaries() throws IOException {
        String allocation = read(DOCUMENT);

        for (String required : new String[] {
                "sc-pf7-comment-replies-allocation-v1",
                "APPROVED / IMPLEMENTATION_AUTHORITY_GRANTED",
                "CONTRACT_ID=comment-replies-v1",
                "STORAGE_FIELD=comments.parent_comment_id",
                "MAX_DEPTH=1",
                "PARENT_SAME_POST=YES",
                "PARENT_VISIBLE_AT_CREATE=YES",
                "READ_SHAPE=FLAT_PAGE_WITH_PARENT_COMMENT_ID",
                "65_comment_replies.sql",
                "66_comment_replies_smoke_test.sql",
                "SQL `67+` remains unallocated",
                "author-deleted parent is rejected",
                "moderation-deleted parent is rejected",
                "cross-post parent is rejected",
                "reply-to-reply is rejected",
                "existing content-only comment creation still creates a top-level comment"
        }) {
            assertTrue(allocation.contains(required), "PF7 allocation missing: " + required);
        }
    }

    @Test
    void allocationDoesNotGrantThreadNotificationRankingSearchOrGovernanceBypass() throws IOException {
        String allocation = read(DOCUMENT);

        assertTrue(allocation.contains("PF7 does not allocate arbitrary-depth threads, notifications, mentions, reactions"));
        assertTrue(allocation.contains("PF7 is not a recommendation, exposure, feedback, ranking or search feature"));
        assertTrue(allocation.contains("Broad allowlist expansion, disabling a static gate"));
        assertTrue(allocation.contains("deployment or production activation"));
        assertTrue(allocation.contains("any SQL after 66"));

        assertFalse(allocation.contains("MAX_DEPTH=UNBOUNDED"));
        assertFalse(allocation.contains("NOTIFICATION_FEATURE=YES"));
        assertFalse(allocation.contains("RECOMMENDATION_FEATURE=YES"));
        assertFalse(allocation.contains("SEARCH_FEATURE=YES"));
        assertFalse(allocation.contains("SQL `67+` allocated"));
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
