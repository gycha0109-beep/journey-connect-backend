package com.jc.backend.verification;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("p1-verification")
class P1RecommendationStaticTest {

    @Test
    void p0CoreBaselineRemainsByteExactExceptForTheDeclaredBuildTaskExtension() throws Exception {
        Path manifest = RepositoryLayout.resolve("verification/P0_8_CORE_BASELINE_SHA256.txt");
        List<String> mismatches = new ArrayList<>();
        int checked = 0;
        for (String line : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            int separator = line.indexOf("  ");
            assertThat(separator).as(line).isGreaterThan(0);
            String expected = line.substring(0, separator);
            String relative = line.substring(separator + 2);
            if (relative.equals("jc-recommendation-core/build.gradle.kts")) {
                continue;
            }
            checked++;
            Path path = RepositoryLayout.resolve(relative);
            if (!Files.exists(path) || !StaticContractSupport.sha256(path).equals(expected)) {
                mismatches.add(relative);
            }
        }
        assertThat(checked).isEqualTo(239);
        assertThat(mismatches).isEmpty();

        String build = RepositoryLayout.read("jc-recommendation-core/build.gradle.kts");
        assertThat(build)
                .contains("p1CoreContractTest")
                .contains("com.jc.recommendation.p1.P1CoreContractTest")
                .contains("dependsOn(p1CoreContractTest)");
    }

    @Test
    void p0DatabaseScriptsRemainExactAndP1CanonicalCopiesAreExact() throws Exception {
        Path p0 = RepositoryLayout.resolve("database/journey-connect-db-v2.5");
        Path p1 = RepositoryLayout.resolve("database/journey-connect-db-v2.6");
        Path canonical = RepositoryLayout.resolve("jc-backend/src/test/resources/db/canonical");
        for (int index = 1; index <= 22; index++) {
            final String prefix = String.format("%02d_", index);
            Path source;
            try (var stream = Files.list(p0)) {
                source = stream.filter(path -> path.getFileName().toString().startsWith(prefix))
                        .findFirst()
                        .orElseThrow();
            }
            StaticContractSupport.assertExactCopy(source, p1.resolve(source.getFileName()), "P0 DB baseline");
            StaticContractSupport.assertExactCopy(source, canonical.resolve(source.getFileName()), "canonical P0 DB baseline");
        }
        for (String script : List.of(
                "23_recommendation_p1_profile_policy.sql",
                "24_recommendation_p1_profile_policy_smoke_test.sql")) {
            StaticContractSupport.assertExactCopy(p1.resolve(script), canonical.resolve(script), "canonical P1 DB script");
        }
    }

    @Test
    void p1CoreIsPureVersionedDeterministicAndRuntimeIsRollbackSafe() throws Exception {
        Path p1Core = RepositoryLayout.resolve("jc-recommendation-core/src/main/java/com/jc/recommendation/p1");
        List<String> forbidden = List.of(
                "org.springframework", "jakarta.persistence", "javax.persistence",
                "JdbcTemplate", "EntityManager", "System.currentTimeMillis", "Instant.now()",
                "Math.random", "new Random(", "SecureRandom");
        try (var stream = Files.walk(p1Core)) {
            for (Path path : stream.filter(Files::isRegularFile).toList()) {
                String source = Files.readString(path);
                for (String token : forbidden) {
                    assertThat(source).as(RepositoryLayout.relative(path)).doesNotContain(token);
                }
            }
        }

        String policies = RepositoryLayout.read(
                "jc-recommendation-core/src/main/java/com/jc/recommendation/p1/policy/P1PolicySelector.java");
        String profile = RepositoryLayout.read(
                "jc-recommendation-core/src/main/java/com/jc/recommendation/p1/profile/BehaviorProfileBuilder.java");
        String runtime = RepositoryLayout.read(
                "jc-backend/src/main/java/com/jc/backend/recommendation/application/RecommendationP1RuntimeService.java");
        String canary = RepositoryLayout.read(
                "jc-backend/src/main/java/com/jc/backend/recommendation/application/RecommendationCanaryService.java");
        String shadow = RepositoryLayout.read(
                "jc-backend/src/main/java/com/jc/backend/recommendation/application/RecommendationShadowService.java");

        assertThat(policies).contains("ranking-policy-v2", "reasons.add", "P1ExperimentAssignment");
        assertThat(profile).contains("referenceTime", "duplicate", "fingerprint");
        assertThat(runtime).contains(
                "@DatabaseTransactional(role = DatabaseRole.RECOMMENDATION)",
                "requireBaselineRun", "storeProfile", "storeAssignment", "storeComparison",
                "verifyPersistence", "return baselineRunId",
                "baselineRunId + \":p1-ranking-input-v1\"");
        assertThat(canary).contains("try {", "selectCanaryRun", "deliveryRunId = run.runId()");
        assertThat(shadow).contains("observeShadow");
    }

    @Test
    void p1SchemaIsAppendOnlyBoundAndEvidenceComplete() throws Exception {
        String sql = RepositoryLayout.read(
                "database/journey-connect-db-v2.6/23_recommendation_p1_profile_policy.sql");
        assertThat(sql).contains(
                "recommendation_p1_profile_snapshot",
                "recommendation_p1_policy_assignment",
                "recommendation_p1_comparison",
                "validate_recommendation_p1_assignment_binding",
                "validate_recommendation_p1_comparison_binding",
                "append_only",
                "baseline_run_id",
                "treatment_run_id",
                "selection_reasons",
                "comparison_fingerprint",
                "replace_recommendation_user_preferences",
                "require_active_user",
                "('history', '역사', 'History'",
                "('running', '러닝', 'Running'");
        assertThat(sql.toLowerCase()).contains("revoke update, delete");

        String preferenceController = RepositoryLayout.read(
                "jc-backend/src/main/java/com/jc/backend/recommendation/api/RecommendationPreferenceController.java");
        String preferenceService = RepositoryLayout.read(
                "jc-backend/src/main/java/com/jc/backend/recommendation/application/RecommendationPreferenceService.java");
        assertThat(preferenceController).contains(
                "@AuthenticationPrincipal Jwt", "@GetMapping", "@PutMapping");
        assertThat(preferenceService).contains(
                "requireBoundUser", "P1FeatureVocabulary.isRegistered",
                "replace_recommendation_user_preferences");
    }
}
