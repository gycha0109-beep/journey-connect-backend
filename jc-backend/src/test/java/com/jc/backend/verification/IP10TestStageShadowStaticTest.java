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
    private static final String POST_DTOS =
            "jc-backend/src/main/java/com/jc/backend/post/PostDtos.java";
    private static final String ADM1_SECURITY_CONFIG =
            "jc-backend/src/main/kotlin/com/jc/backend/config/SecurityConfig.kt";

    @Test
    void productionResourcesAndLegacyBackendRemainProtected() throws Exception {
        Path manifest = RepositoryLayout.resolve("verification/ip9/IP9_POSTCHANGE_BACKEND_PROTECTED_SHA256.txt");
        List<String> lines = Files.readAllLines(manifest, StandardCharsets.UTF_8).stream()
                .filter(line -> !line.isBlank()).toList();
        int approvedPf7ControllerDeltas = 0;
        int approvedPf7DtoDeltas = 0;
        int approvedAdm1SecurityDeltas = 0;
        for (String line : lines) {
            String[] parts = line.trim().split("\\s+", 2);
            assertThat(parts).hasSize(2);
            Path path = RepositoryLayout.resolve(parts[1]);
            String current = sha256(path);
            if (CONTROLLER.equals(parts[1])) {
                assertThat(current).as(parts[1]).isNotEqualTo(parts[0]);
                assertApprovedPf7ControllerBoundary(Files.readString(path));
                approvedPf7ControllerDeltas++;
            } else if (POST_DTOS.equals(parts[1])) {
                assertThat(current).as(parts[1]).isNotEqualTo(parts[0]);
                assertApprovedPf7DtoBoundary(Files.readString(path));
                approvedPf7DtoDeltas++;
            } else if (ADM1_SECURITY_CONFIG.equals(parts[1])) {
                assertThat(current).as(parts[1]).isNotEqualTo(parts[0]);
                assertApprovedAdm1SecurityBoundary(Files.readString(path));
                approvedAdm1SecurityDeltas++;
            } else {
                assertThat(current).as(parts[1]).isEqualTo(parts[0]);
            }
        }
        assertThat(approvedPf7ControllerDeltas).isEqualTo(1);
        assertThat(approvedPf7DtoDeltas).isEqualTo(1);
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
    void activationIsProfileAndExplicitAllowGuardedWithNoProductionCutoverAuthority() throws Exception {
        String config = RepositoryLayout.read(
                "jc-backend/src/main/java/com/jc/backend/search/shadow/stage/StageSearchShadowConfiguration.java");
        String condition = RepositoryLayout.read(
                "jc-backend/src/main/java/com/jc/backend/search/shadow/stage/StageSearchShadowActivationCondition.java");
        String properties = RepositoryLayout.read(
                "jc-backend/src/main/java/com/jc/backend/search/shadow/stage/StageSearchShadowProperties.java");
        String controller = RepositoryLayout.read(CONTROLLER);
        assertThat(config).contains("StageSearchShadowActivationCondition", "DefaultExploreSearchShadowBridge")
                .doesNotContain("@Profile(\"prod\")", "Repository", "EntityManager", "SearchRunRepository");
        assertThat(condition).contains("activationAllowed");
        assertThat(properties).contains(
                        "SearchShadowWiringConfigV1.TEST_PROFILE",
                        "SearchShadowWiringConfigV1.STAGE_PROFILE",
                        "sample-basis-points")
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

    private static void assertApprovedPf7ControllerBoundary(String source) {
        assertThat(source).contains(
                "private final CommentReplyService commentReplyService;",
                "commentReplyService.comments(postId, userIdOrNull(token), pageable)",
                "commentReplyService.addComment(",
                "request.content(), request.parentCommentId()",
                "postService.deleteComment(userId(token), commentId)");
        assertThat(source).doesNotContain(
                "replyRecommendation",
                "replyExposure",
                "replySearch",
                "replyNotification");
    }

    private static void assertApprovedPf7DtoBoundary(String source) {
        assertThat(source).contains(
                "public record CommentRequest(",
                "Long parentCommentId",
                "public CommentRequest(String content)",
                "this(content, null);",
                "public record CommentView(",
                "this(id, content, author, createdAt, null);");
        assertThat(count(source, "Long parentCommentId")).isEqualTo(2);
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

    private static int count(String value, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
