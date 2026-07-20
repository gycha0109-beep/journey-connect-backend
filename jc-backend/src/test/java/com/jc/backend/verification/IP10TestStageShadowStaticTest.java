package com.jc.backend.verification;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class IP10TestStageShadowStaticTest {
    @Test
    void productionResourcesAndLegacyBackendRemainProtected() throws Exception {
        Path manifest = RepositoryLayout.resolve("verification/ip9/IP9_POSTCHANGE_BACKEND_PROTECTED_SHA256.txt");
        List<String> lines = Files.readAllLines(manifest, StandardCharsets.UTF_8).stream()
                .filter(line -> !line.isBlank()).toList();
        for (String line : lines) {
            String[] parts = line.trim().split("\\s+", 2);
            assertThat(parts).hasSize(2);
            assertThat(sha256(RepositoryLayout.resolve(parts[1]))).as(parts[1]).isEqualTo(parts[0]);
        }
        Path resources = RepositoryLayout.resolve("jc-backend/src/main/resources");
        try (var stream = Files.walk(resources)) {
            for (Path path : stream.filter(Files::isRegularFile).toList()) {
                assertThat(Files.readString(path)).as(RepositoryLayout.relative(path))
                        .doesNotContain("search.shadow.stage", "search-shadow-stage", "search-shadow-test");
            }
        }
    }

    @Test
    void activationIsProfileAndExplicitAllowGuardedWithNoProductionCutoverAuthority() throws Exception {
        String config = RepositoryLayout.read(
                "jc-backend/src/main/java/com/jc/backend/search/shadow/stage/StageSearchShadowConfiguration.java");
        String condition = RepositoryLayout.read(
                "jc-backend/src/main/java/com/jc/backend/search/shadow/stage/StageSearchShadowActivationCondition.java");
        String properties = RepositoryLayout.read(
                "jc-backend/src/main/java/com/jc/backend/search/shadow/stage/StageSearchShadowProperties.java");
        String controller = RepositoryLayout.read(
                "jc-backend/src/main/java/com/jc/backend/post/PostController.java");
        assertThat(config).contains("StageSearchShadowActivationCondition", "DefaultExploreSearchShadowBridge")
                .doesNotContain("@Profile(\"prod\")", "Repository", "EntityManager", "SearchRunRepository");
        assertThat(condition).contains("activationAllowed");
        assertThat(properties).contains("search-shadow-test", "search-shadow-stage", "sample-basis-points")
                .doesNotContain("SHADOW_CANDIDATE", "production enabled");
        assertThat(controller).contains("return ApiResponse.ok(legacyResponse);")
                .doesNotContain("stageSearch", "SearchRuntime", "SearchShadowDispatchReceiptV1");
    }

    @Test
    void executorAndEvidenceAreBoundedNonPersistentAndPrivacySafe() throws Exception {
        String taskExecutor = RepositoryLayout.read(
                "jc-backend/src/main/java/com/jc/backend/search/shadow/stage/StageSearchShadowTaskExecutor.java");
        String runtimeExecutor = RepositoryLayout.read(
                "jc-backend/src/main/java/com/jc/backend/search/shadow/stage/StageBoundedSearchShadowExecutionPort.java");
        String recorder = RepositoryLayout.read(
                "jc-backend/src/main/java/com/jc/backend/search/shadow/stage/InMemoryStageSearchShadowComparisonLogPort.java");
        String all = taskExecutor + runtimeExecutor + recorder;
        assertThat(all).contains("ArrayBlockingQueue", "setDaemon(true)", "shutdownNow")
                .doesNotContain("ForkJoinPool", "newCachedThreadPool", "java.sql", "Kafka", "INSERT ", "UPDATE ");
        assertThat(recorder).contains("memory-only").doesNotContain("rawQuery", "keyword", "userId", "sessionId");
    }

    @Test
    void ip10GradleTasksExistAndDoNotIgnoreFailures() throws Exception {
        String build = RepositoryLayout.read("jc-backend/build.gradle.kts");
        assertThat(build).contains(
                "ip10TestStageShadowActivationRegression",
                "ip10CombinedExternalRegressionClosure",
                "ip9ControlledBackendHookRegression",
                "ip8SearchRegressionClosure");
        assertThat(build).doesNotContain("ignoreFailures", "isIgnoreFailures");
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
