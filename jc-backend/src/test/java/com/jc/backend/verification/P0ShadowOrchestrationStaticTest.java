package com.jc.backend.verification;

import static com.jc.backend.verification.StaticContractSupport.assertExactCopy;
import static com.jc.backend.verification.StaticContractSupport.failContract;
import static com.jc.backend.verification.StaticContractSupport.requireContains;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("p0-verification")
class P0ShadowOrchestrationStaticTest {
    private static final Path V22 = RepositoryLayout.resolve("database/journey-connect-db-v2.2");
    private static final Path V23 = RepositoryLayout.resolve("database/journey-connect-db-v2.3");
    private static final Path CANONICAL = RepositoryLayout.resolve(
            "jc-backend/src/test/resources/db/canonical");

    private static final List<String> BASE = List.of(
            "01_initial_schema.sql",
            "02_seed.sql",
            "03_smoke_test.sql",
            "04_admin_support.sql",
            "05_security_roles.sql",
            "06_security_smoke_test.sql",
            "07_recommendation_storage.sql",
            "08_recommendation_security_roles.sql",
            "09_recommendation_smoke_test.sql",
            "10_backend_runtime.sql",
            "11_backend_runtime_security_roles.sql",
            "12_backend_runtime_smoke_test.sql",
            "13_backend_role_routing.sql",
            "14_backend_role_routing_smoke_test.sql",
            "15_backend_role_runtime_fix.sql",
            "16_backend_role_runtime_fix_smoke_test.sql");
    private static final List<String> ORCHESTRATION = List.of(
            "17_recommendation_run_exploration_partition_fix.sql",
            "18_recommendation_run_exploration_partition_fix_smoke_test.sql");

    @Test
    void shadowOrchestrationRemainsFailClosedReplayableAndAppendOnly() throws IOException {
        for (String name : BASE) {
            assertExactCopy(V22.resolve(name), V23.resolve(name), "v2.2 baseline drift in DB v2.3");
        }
        for (String name : java.util.stream.Stream.concat(BASE.stream(), ORCHESTRATION.stream()).toList()) {
            assertExactCopy(V23.resolve(name), CANONICAL.resolve(name), "canonical PostgreSQL SQL drift");
        }

        String partitionFix = Files.readString(V23.resolve(ORCHESTRATION.get(0)));
        String partitionSmoke = Files.readString(V23.resolve(ORCHESTRATION.get(1)));
        requireContains(partitionFix,
                "input_count = final_ranked_candidate_count + terminal_candidate_count",
                "final recommendation partition");
        requireContains(partitionFix,
                "final_ranked_candidate_count >= scored_candidate_count",
                "exploration insertion lower bound");
        requireContains(partitionFix,
                "final_ranked_candidate_count <= input_count",
                "final ranking upper bound");
        requireContains(partitionSmoke, "pg_get_constraintdef", "constraint metadata smoke");
        requireContains(partitionSmoke, "ROLLBACK;", "orchestration smoke rollback");

        String properties = RepositoryLayout.read(
                "jc-backend/src/main/java/com/jc/backend/recommendation/config/RecommendationProperties.java");
        String decider = RepositoryLayout.read(
                "jc-backend/src/main/java/com/jc/backend/recommendation/application/RecommendationModeDecider.java");
        String orchestration = RepositoryLayout.read(
                "jc-backend/src/main/java/com/jc/backend/recommendation/application/RecommendationOrchestrationService.java");
        String shadow = RepositoryLayout.read(
                "jc-backend/src/main/java/com/jc/backend/recommendation/application/RecommendationShadowService.java");
        String controller = RepositoryLayout.read(
                "jc-backend/src/main/java/com/jc/backend/post/PostController.java");
        String feedService = RepositoryLayout.read(
                "jc-backend/src/main/java/com/jc/backend/recommendation/application/RecommendationFeedService.java");
        String runStore = RepositoryLayout.read(
                "jc-backend/src/main/java/com/jc/backend/recommendation/persistence/RecommendationRunStore.java");
        String sample = RepositoryLayout.read("jc-backend/src/main/resources/application.yml.sample");
        String initializer = RepositoryLayout.read(
                "jc-backend/src/test/java/com/jc/backend/CanonicalPostgresInitializer.java");
        String cleanup = RepositoryLayout.read(
                "jc-backend/src/test/resources/db/canonical/cleanup.sql");

        requireContains(properties, "private Mode mode = Mode.OFF;", "OFF safe default");
        requireContains(decider, "properties.getMode() == Mode.LIVE", "LIVE startup block");
        requireContains(properties, "mode == Mode.CANARY", "explicit CANARY release controls");
        if (orchestration.contains("public final class RecommendationOrchestrationService")) {
            failContract("AOP-managed orchestration service must remain proxyable");
        }
        requireContains(orchestration, "DatabaseRole.RECOMMENDATION", "recommendation transaction role");
        requireContains(orchestration, "DatabasePropagation.REQUIRES_NEW", "isolated shadow transaction");
        requireContains(orchestration, "RunMode.SHADOW", "persisted shadow mode");
        requireContains(orchestration, "SnapshotKind.RANKING_RESULT_V1", "replay result snapshot");
        requireContains(shadow, "catch (RuntimeException exception)", "legacy feed fail-open boundary");
        requireContains(sample, "mode: ${RECOMMENDATION_MODE:OFF}", "configuration safe default");
        requireContains(runStore,
                "inputCount must equal final-ranked + terminal candidates",
                "Java final partition validation");
        requireContains(cleanup, "TRUNCATE TABLE", "append-only recommendation test cleanup");
        requireContains(cleanup, "public.recommendation_snapshot;", "recommendation snapshot test cleanup");
        for (String name : ORCHESTRATION) {
            requireContains(initializer, "\"" + name + "\"", "PostgreSQL bootstrap migration " + name);
        }

        requireContains(controller, "recommendationFeedService.feed(", "central feed rollout boundary");
        int legacyResponse = feedService.indexOf("postService.feed(");
        int shadowInvocation = feedService.indexOf("shadowService.observeHomeFeed(");
        if (legacyResponse < 0 || shadowInvocation < 0 || legacyResponse > shadowInvocation) {
            failContract("legacy feed response must be computed before SHADOW observation");
        }
    }
}
