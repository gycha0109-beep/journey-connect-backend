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
class P0PersistenceReplayStaticTest {
    private static final Path V23 = RepositoryLayout.resolve("database/journey-connect-db-v2.3");
    private static final Path V24 = RepositoryLayout.resolve("database/journey-connect-db-v2.4");
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
            "18_recommendation_run_exploration_partition_fix_smoke_test.sql");
    private static final List<String> REPLAY = List.of(
            "19_recommendation_replay_audit.sql",
            "20_recommendation_replay_audit_smoke_test.sql");

    @Test
    void persistedReplayAndCanaryReadinessRemainFailClosed() throws IOException {
        for (String name : BASE) {
            assertExactCopy(V23.resolve(name), V24.resolve(name), "v2.3 baseline drift in DB v2.4");
        }
        for (String name : java.util.stream.Stream.concat(BASE.stream(), REPLAY.stream()).toList()) {
            assertExactCopy(V24.resolve(name), CANONICAL.resolve(name), "canonical PostgreSQL SQL drift");
        }

        String auditSql = Files.readString(V24.resolve(REPLAY.get(0)));
        String smokeSql = Files.readString(V24.resolve(REPLAY.get(1)));
        requireContains(auditSql, "CREATE TABLE public.recommendation_replay_audit", "replay audit table");
        requireContains(auditSql, "UNIQUE (run_id, evaluator_version, evaluator_build_id)", "evaluator idempotency");
        requireContains(auditSql, "recommendation_replay_audit_append_only", "append-only replay audit");
        requireContains(auditSql, "GRANT SELECT, INSERT", "recommendation role write boundary");
        requireContains(auditSql, "GRANT SELECT ON public.recommendation_replay_audit TO jc_admin", "admin inspection only");
        requireContains(smokeSql, "EXCEPTION WHEN SQLSTATE '55000'", "append-only runtime smoke");
        requireContains(smokeSql, "ROLLBACK;", "smoke rollback");

        String replayService = RepositoryLayout.read(
                "jc-backend/src/main/java/com/jc/backend/recommendation/application/RecommendationReplayService.java");
        String shadowService = RepositoryLayout.read(
                "jc-backend/src/main/java/com/jc/backend/recommendation/application/RecommendationShadowService.java");
        String readinessService = RepositoryLayout.read(
                "jc-backend/src/main/java/com/jc/backend/recommendation/application/RecommendationCanaryReadinessService.java");
        String properties = RepositoryLayout.read(
                "jc-backend/src/main/java/com/jc/backend/recommendation/config/RecommendationProperties.java");
        String initializer = RepositoryLayout.read(
                "jc-backend/src/test/java/com/jc/backend/CanonicalPostgresInitializer.java");
        String cleanup = RepositoryLayout.read(
                "jc-backend/src/test/resources/db/canonical/cleanup.sql");

        if (replayService.contains("public final class RecommendationReplayService")) {
            failContract("AOP-managed replay service must remain proxyable");
        }
        requireContains(replayService, "DatabasePropagation.REQUIRES_NEW", "isolated persistence replay transaction");
        requireContains(replayService, "RankingV3FullResultCollector", "frozen Java replay collector");
        requireContains(replayService, "Arrays.equals(encoded.bytes(), bundle.rankingResult().canonicalPayload())", "exact result snapshot bytes");
        requireContains(replayService, "RecommendationHashing.sha256(encoded.bytes())", "result fingerprint comparison");
        requireContains(shadowService, "replayService.audit(result.runId())", "automatic SHADOW persistence replay");
        requireContains(shadowService, "if (!replay.exactMatch())", "non-exact SHADOW fail-open boundary");
        requireContains(properties, "private boolean replayAuditEnabled = true;", "replay audit safe default");
        requireContains(readinessService, "missing_replay_audit", "missing audit readiness blocker");
        requireContains(readinessService, "non_exact_replay", "non-exact readiness blocker");
        requireContains(readinessService, "p95_latency_exceeded", "latency readiness blocker");
        if (readinessService.contains("setMode(")) {
            failContract("Readiness evaluation must never promote recommendation mode");
        }
        for (String name : REPLAY) {
            requireContains(initializer, "\"" + name + "\"", "PostgreSQL bootstrap migration " + name);
        }
        requireContains(cleanup, "public.recommendation_replay_audit", "replay audit test cleanup");
    }
}
