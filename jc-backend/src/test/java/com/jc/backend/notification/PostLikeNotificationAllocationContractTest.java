package com.jc.backend.notification;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PostLikeNotificationAllocationContractTest {

    private static final String DOCUMENT =
            "docs/platform/governance/SC-PF10-POST-LIKE-NOTIFICATION-ALLOCATION.md";

    @Test
    void allocationLocksAppliedLikeNotificationContractAndSqlSuccessors() throws IOException {
        String allocation = read(DOCUMENT);

        for (String required : new String[] {
                "sc-pf10-post-like-notification-v1",
                "APPROVED / IMPLEMENTATION_AUTHORITY_GRANTED",
                "CONTRACT_ID=post-like-notification-v1",
                "SOURCE_ENDPOINT=POST /api/v1/posts/{postId}/likes",
                "SOURCE_ACTION=LIKE",
                "SOURCE_RESULT=APPLIED",
                "NOTIFICATION_TYPE=post_like",
                "TARGET_TYPE=post",
                "WRITE_ROLE=APP",
                "DEDUPE_KEY=post_like:{postId}:{actorId}",
                "SELF_NOTIFICATION=SUPPRESSED",
                "TRANSACTION=LIKE_STATE_AND_BEHAVIOR_EVENT_AND_NOTIFICATION_ATOMIC",
                "SQL_69=ALLOCATED",
                "SQL_70=ALLOCATED",
                "SQL_71_PLUS=UNALLOCATED"
        }) {
            assertTrue(allocation.contains(required), "PF10 allocation missing: " + required);
        }
    }

    @Test
    void allocationPreservesRecommendationAuthorityAndRequiresCoordinatorAtomicity() throws IOException {
        String allocation = read(DOCUMENT);

        for (String required : new String[] {
                "RecommendationPostInteractionService",
                "RecommendationPostInteractionStore",
                "public.apply_recommendation_post_interaction(...)` lineage is authoritative",
                "PostLikeNotificationCoordinator",
                "if result == APPLIED",
                "notification insert fails, the like state transition and recommendation behavior event roll back",
                "An unlike followed by a later re-like does not create a second notification row"
        }) {
            assertTrue(allocation.contains(required), "PF10 authority rule missing: " + required);
        }

        assertTrue(allocation.contains("No scoring, ranking, recommendation profile"));
        assertTrue(allocation.contains("must not create a second like writer"));
        assertTrue(allocation.contains("must not be copied as authority"));
    }

    @Test
    void allocationRestrictsSql69ToCheckDomainAndKeepsLaterSqlUnallocated() throws IOException {
        String allocation = read(DOCUMENT);

        assertTrue(allocation.contains("69_post_like_notification_type.sql"));
        assertTrue(allocation.contains("70_post_like_notification_type_smoke_test.sql"));
        assertTrue(allocation.contains("post_like -> post"));
        assertTrue(allocation.contains("SQL69 must not create or alter any table column, index, sequence, function, procedure, trigger, role, or grant"));
        assertTrue(allocation.contains("SQL `71+` remains unallocated"));

        Path production = repositoryRoot().resolve("database/journey-connect-db-v2.7");
        Path mirror = repositoryRoot().resolve("jc-backend/src/test/resources/db/canonical");
        try (var files = Files.list(production)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().matches("^(69|70|71)_.*\\.sql$")));
        }
        try (var files = Files.list(mirror)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().matches("^(69|70|71)_.*\\.sql$")));
        }
    }

    @Test
    void excludedProductSurfacesRemainExplicitlyOutOfScope() throws IOException {
        String allocation = read(DOCUMENT);

        for (String excluded : new String[] {
                "report-resolution notifications",
                "bookmark/save notifications",
                "push/email/SMS",
                "WebSocket/SSE",
                "Google login or external identity",
                "frontend work",
                "deployment or production traffic activation"
        }) {
            assertTrue(allocation.contains(excluded), "PF10 non-goal missing: " + excluded);
        }

        assertFalse(allocation.contains("SQL_71_PLUS=ALLOCATED"));
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
