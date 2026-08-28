package com.jc.backend.user;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UserFollowGraphAllocationContractTest {

    private static final String DOCUMENT =
            "docs/platform/governance/SC-PF11-USER-FOLLOW-GRAPH-ALLOCATION.md";

    @Test
    void allocationLocksRuntimeOnlyFollowMutationContract() throws IOException {
        String allocation = read(DOCUMENT);

        for (String required : new String[] {
                "sc-pf11-user-follow-graph-v1",
                "APPROVED / IMPLEMENTATION_AUTHORITY_GRANTED",
                "CONTRACT_ID=user-follow-graph-v1",
                "FOLLOW_ENDPOINT=POST /api/v1/users/{userId}/follow",
                "UNFOLLOW_ENDPOINT=DELETE /api/v1/users/{userId}/follow",
                "HTTP_STATUS=204",
                "AUTHENTICATION=REQUIRED",
                "ACTOR_SOURCE=VERIFIED_JWT_USER_ID",
                "TARGET_USER=ACTIVE_ONLY",
                "SELF_FOLLOW=REJECTED",
                "FOLLOW_CREATE=IDEMPOTENT",
                "UNFOLLOW_DELETE=IDEMPOTENT",
                "STORAGE=public.follows",
                "WRITE_ROLE=APP",
                "SQL_71_PLUS=UNALLOCATED"
        }) {
            assertTrue(allocation.contains(required), "PF11 allocation missing: " + required);
        }
    }

    @Test
    void allocationReusesExistingCanonicalFollowRelationAndPrivileges() throws IOException {
        String schema = read("database/journey-connect-db-v2.7/01_initial_schema.sql");
        String security = read("database/journey-connect-db-v2.7/05_security_roles.sql");
        String allocation = read(DOCUMENT);

        assertTrue(schema.contains("CREATE TABLE public.follows ("));
        assertTrue(schema.contains("PRIMARY KEY (follower_id, following_id)"));
        assertTrue(schema.contains("CONSTRAINT follows_not_self_check CHECK (follower_id <> following_id)"));
        assertTrue(security.contains("public.post_likes, public.bookmarks, public.follows TO jc_app;"));
        assertTrue(security.contains("public.post_tags, public.post_likes, public.bookmarks, public.follows TO jc_app;"));
        assertTrue(security.contains("FROM public.follows f"));
        assertTrue(security.contains("f.follower_id = p_user_id"));
        assertTrue(security.contains("f.following_id = p.author_id"));

        assertTrue(allocation.contains("PF11 must reuse those existing schema and privilege contracts unchanged."));
        assertTrue(allocation.contains("No SQL file is required to implement PF11."));
    }

    @Test
    void allocationKeepsSql71PlusUnallocatedAndRuntimeAbsent() throws IOException {
        Path root = repositoryRoot();
        Path production = root.resolve("database/journey-connect-db-v2.7");
        Path mirror = root.resolve("jc-backend/src/test/resources/db/canonical");

        assertNoSql71Plus(production);
        assertNoSql71Plus(mirror);

        String initializer = read("jc-backend/src/test/java/com/jc/backend/CanonicalPostgresInitializer.java");
        assertTrue(initializer.contains("69_post_like_notification_type.sql"));
        assertTrue(initializer.contains("70_post_like_notification_type_smoke_test.sql"));
        assertFalse(initializer.contains("71_"));

        String controller = read("jc-backend/src/main/java/com/jc/backend/user/UserController.java");
        assertFalse(controller.contains("/follow"));
        assertFalse(Files.exists(root.resolve("jc-backend/src/main/java/com/jc/backend/user/UserFollowService.java")));
        assertFalse(Files.exists(root.resolve("jc-backend/src/main/java/com/jc/backend/user/UserFollowRepository.java")));
    }

    @Test
    void allocationKeepsAdjacentSocialSurfacesOutOfScope() throws IOException {
        String allocation = read(DOCUMENT);

        for (String excluded : new String[] {
                "follower/following list endpoints",
                "follower/following counts",
                "public-profile follow-state fields",
                "follow notifications",
                "social-graph recommendation features",
                "new recommendation behavior events",
                "private-account follow requests",
                "blocking or muting",
                "WebSocket/SSE/push/email/SMS",
                "frontend work",
                "deployment or production traffic activation",
                "SQL `71+`"
        }) {
            assertTrue(allocation.contains(excluded), "PF11 non-goal missing: " + excluded);
        }

        assertFalse(allocation.contains("SQL_71_PLUS=ALLOCATED"));
    }

    private static void assertNoSql71Plus(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            assertFalse(files
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.matches("^[0-9]{2}_.+\\.sql$"))
                    .anyMatch(name -> Integer.parseInt(name.substring(0, 2)) >= 71));
        }
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
