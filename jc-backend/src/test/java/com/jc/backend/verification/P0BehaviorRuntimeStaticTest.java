package com.jc.backend.verification;

import static com.jc.backend.verification.StaticContractSupport.assertExactCopy;
import static com.jc.backend.verification.StaticContractSupport.requireContains;
import static com.jc.backend.verification.StaticContractSupport.requireNotContains;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("p0-verification")
class P0BehaviorRuntimeStaticTest {

    private static final Path V24 = RepositoryLayout.resolve("database/journey-connect-db-v2.4");
    private static final Path V25 = RepositoryLayout.resolve("database/journey-connect-db-v2.5");
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
            "16_backend_role_runtime_fix_smoke_test.sql",
            "17_recommendation_run_exploration_partition_fix.sql",
            "18_recommendation_run_exploration_partition_fix_smoke_test.sql",
            "19_recommendation_replay_audit.sql",
            "20_recommendation_replay_audit_smoke_test.sql");

    @Test
    void behaviorRuntimeAddsOnlyVersionedSqlAndKeepsPriorCanonicalBytes() throws Exception {
        for (String name : BASE) {
            assertExactCopy(V24.resolve(name), V25.resolve(name), "DB v2.4 behavior runtime drift");
            assertExactCopy(V25.resolve(name), CANONICAL.resolve(name), "canonical SQL drift");
        }
        for (String name : List.of(
                "21_recommendation_behavior_runtime.sql",
                "22_recommendation_behavior_runtime_smoke_test.sql")) {
            assertExactCopy(V25.resolve(name), CANONICAL.resolve(name), "behavior runtime SQL drift");
            SqlLexicalVerifier.verify(V25.resolve(name));
        }
    }

    @Test
    void behaviorRuntimeIsRunBoundIdempotentAtomicAndLeastPrivilege() throws Exception {
        String sql = Files.readString(V25.resolve("21_recommendation_behavior_runtime.sql"));
        String smoke = Files.readString(V25.resolve("22_recommendation_behavior_runtime_smoke_test.sql"));
        String behaviorStore = RepositoryLayout.read(
                "jc-backend/src/main/java/com/jc/backend/recommendation/persistence/RecommendationBehaviorStore.java");
        String interactionStore = RepositoryLayout.read(
                "jc-backend/src/main/java/com/jc/backend/recommendation/persistence/RecommendationPostInteractionStore.java");
        String behaviorController = RepositoryLayout.read(
                "jc-backend/src/main/java/com/jc/backend/recommendation/api/RecommendationBehaviorController.java");
        String postController = RepositoryLayout.read(
                "jc-backend/src/main/java/com/jc/backend/post/PostController.java");
        String canary = RepositoryLayout.read(
                "jc-backend/src/main/java/com/jc/backend/recommendation/application/RecommendationCanaryService.java");
        String initializer = RepositoryLayout.read(
                "jc-backend/src/test/java/com/jc/backend/CanonicalPostgresInitializer.java");

        requireContains(sql, "recommendation_run_candidate candidate", "run candidate binding");
        requireContains(sql, "apply_recommendation_post_interaction", "atomic interaction function");
        requireContains(sql, "RETURN 'idempotency_conflict'", "idempotency conflict result");
        requireContains(sql, "v_changed = 0", "no-change event suppression");
        requireContains(sql, "pg_advisory_xact_lock", "concurrent idempotency serialization");
        requireContains(sql, "clock_timestamp() + interval '5 minutes'", "interaction time bound");
        requireContains(sql, "TO jc_app", "APP-only function grant");
        requireNotContains(sql,
                "GRANT INSERT ON public.recommendation_behavior_event TO jc_app",
                "direct APP behavior insert grant");
        requireContains(smoke, "Atomic like mutation failed", "atomic mutation smoke assertion");
        requireContains(smoke, "jc_app must not insert behavior history directly", "least privilege smoke");
        requireContains(behaviorStore, "where event_id = ? or idempotency_key = ?", "dual idempotency lookup");
        requireContains(behaviorStore, "verifyRunBinding", "application run binding precheck");
        requireContains(behaviorStore, "pg_advisory_xact_lock", "direct behavior idempotency lock");
        requireContains(interactionStore, "apply_recommendation_post_interaction", "atomic function call");
        requireContains(behaviorController, "@PostMapping(\"/events\")", "behavior API endpoint");
        requireContains(postController, "X-Recommendation-Run-Id", "interaction run header");
        requireContains(canary, "CursorPageResponse.recommendation", "run ID delivery metadata");
        requireContains(initializer, "\"21_recommendation_behavior_runtime.sql\"", "behavior migration bootstrap");
        requireContains(initializer, "\"22_recommendation_behavior_runtime_smoke_test.sql\"", "behavior smoke bootstrap");
    }
}
