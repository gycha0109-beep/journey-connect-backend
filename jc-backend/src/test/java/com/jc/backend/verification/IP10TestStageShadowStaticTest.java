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
    private static final String CONTROLLER =
            "jc-backend/src/main/java/com/jc/backend/post/PostController.java";
    private static final String SEARCH_SERVICE =
            "jc-backend/src/main/java/com/jc/backend/intelligence/search/RecommendationSearchService.java";
    private static final String ADM1_SECURITY_CONFIG =
            "jc-backend/src/main/kotlin/com/jc/backend/config/SecurityConfig.kt";

    @Test
    void productionResourcesAndLegacyBackendRemainProtected() throws Exception {
        Path manifest = RepositoryLayout.resolve("verification/ip9/IP9_POSTCHANGE_BACKEND_PROTECTED_SHA256.txt");
        List<String> lines = Files.readAllLines(manifest, StandardCharsets.UTF_8).stream()
                .filter(line -> !line.isBlank()).toList();
        int approvedControllerDeltas = 0;
        int approvedAdm1SecurityDeltas = 0;
        for (String line : lines) {
            String[] parts = line.trim().split("\\s+", 2);
            assertThat(parts).hasSize(2);
            Path path = RepositoryLayout.resolve(parts[1]);
            String current = sha256(path);
            if (CONTROLLER.equals(parts[1])) {
                assertThat(current).as(parts[1]).isNotEqualTo(parts[0]);
                assertApprovedSearchControllerBoundary(Files.readString(path));
                approvedControllerDeltas++;
            } else if (ADM1_SECURITY_CONFIG.equals(parts[1])) {
                assertThat(current).as(parts[1]).isNotEqualTo(parts[0]);
                assertApprovedAdm1SecurityBoundary(Files.readString(path));
                approvedAdm1SecurityDeltas++;
            } else {
                assertThat(current).as(parts[1]).isEqualTo(parts[0]);
            }
        }
        assertThat(approvedControllerDeltas).isEqualTo(1);
        assertThat(approvedAdm1SecurityDeltas).isEqualTo(1);
        Path resources = RepositoryLayout.resolve("jc-backend/src/main/resources");
        try (var stream = Files.walk(resources)) {
            for (Path path : stream.filter(Files::isRegularFile).toList()) {
                assertThat(Files.readString(path)).as(RepositoryLayout.relative(path))
                        .doesNotContain("search.shadow.stage", "search-shadow-stage", "search-shadow-test");
            }
        }
    }

    @Test
    void activationIsProfileAndExplicitAllowGuardedWithNoShadowCutoverAuthority() throws Exception {
        String config = RepositoryLayout.read(
                "jc-backend/src/main/java/com/jc/backend/search/shadow/stage/StageSearchShadowConfiguration.java");
        String condition = RepositoryLayout.read(
                "jc-backend/src/main/java/com/jc/backend/search/shadow/stage/StageSearchShadowActivationCondition.java");
        String properties = RepositoryLayout.read(
                "jc-backend/src/main/java/com/jc/backend/search/shadow/stage/StageSearchShadowProperties.java");
        String controller = RepositoryLayout.read(CONTROLLER);
        String searchService = RepositoryLayout.read(SEARCH_SERVICE);
        assertThat(config).contains("StageSearchShadowActivationCondition", "DefaultExploreSearchShadowBridge")
                .doesNotContain("@Profile(\"prod\")", "Repository", "EntityManager", "SearchRunRepository");
        assertThat(condition).contains("activationAllowed");
        assertThat(properties).contains(
                        "SearchShadowWiringConfigV1.TEST_PROFILE",
                        "SearchShadowWiringConfigV1.STAGE_PROFILE",
                        "sample-basis-points")
                .doesNotContain("SHADOW_CANDIDATE", "production enabled");
        assertApprovedSearchControllerBoundary(controller);
        assertThat(searchService).contains(
                        "${app.recommendation.search.enabled:false}",
                        "return legacyResponse;",
                        "catch (RuntimeException exception)")
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

    private static void assertApprovedSearchControllerBoundary(String source) {
        assertThat(source).contains(
                "RecommendationSearchService recommendationSearchService",
                "PageResponse<PostDtos.Summary> legacyResponse = postService.explore(keyword, region, pageable);",
                "exploreSearchShadowBridge.afterExplore(keyword, region, pageable, legacyResponse);",
                "return ApiResponse.ok(recommendationSearchService.explore(",
                "userIdOrNull(token)",
                "legacyResponse));");
        assertThat(source).doesNotContain(
                "return ApiResponse.ok(exploreSearchShadowBridge",
                "SearchShadowDispatchReceiptV1");
    }

    private static void assertApprovedAdm1SecurityBoundary(String source) {
        assertThat(source).contains(
                "\"/api/admin\"",
                "\"/api/admin/**\"",
                ".hasRole(\"ADMIN\")",
                "JwtAuthenticationConverter",
                "ROLE_ADMIN",
                "ADMIN_ACCESS_DENIED");
        assertThat(source).doesNotContain(
                ".requestMatchers(\"/api/admin/**\").permitAll()",
                "search.shadow.stage",
                "SearchShadowDispatchReceiptV1");
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
