package com.jc.backend.verification;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class IP9ControlledBackendHookStaticTest {
    private static final String CONTROLLER =
            "jc-backend/src/main/java/com/jc/backend/post/PostController.java";
    private static final String POST_DTOS =
            "jc-backend/src/main/java/com/jc/backend/post/PostDtos.java";
    private static final String RCA2_FEED_SERVICE =
            "jc-backend/src/main/java/com/jc/backend/recommendation/application/RecommendationFeedService.java";
    private static final String PF10_INTERACTION_SERVICE =
            "jc-backend/src/main/java/com/jc/backend/recommendation/application/RecommendationPostInteractionService.java";
    private static final String PF10_ALLOCATION =
            "docs/platform/governance/SC-PF10-POST-LIKE-NOTIFICATION-ALLOCATION.md";
    private static final String ADM1_SECURITY_CONFIG =
            "jc-backend/src/main/kotlin/com/jc/backend/config/SecurityConfig.kt";

    @Test
    void controllerUsesLegacyServiceResultAsTheOnlyResponseAuthority() throws Exception {
        String source = RepositoryLayout.read(CONTROLLER);
        assertThat(source).contains(
                "PageResponse<PostDtos.Summary> legacyResponse = postService.explore(keyword, region, pageable);",
                "exploreSearchShadowBridge.afterExplore(keyword, region, pageable, legacyResponse);",
                "return ApiResponse.ok(legacyResponse);");
        assertThat(source).doesNotContain(
                "return ApiResponse.ok(exploreSearchShadowBridge",
                "return exploreSearchShadowBridge",
                "SearchShadowDispatchReceiptV1");
        assertThat(count(source, "postService.explore(keyword, region, pageable)"))
                .as("legacy service invocation count")
                .isEqualTo(1);
    }

    @Test
    void defaultConfigurationCannotActivateProductionShadow() throws Exception {
        String configuration = RepositoryLayout.read(
                "jc-backend/src/main/java/com/jc/backend/search/shadow/SearchShadowBackendConfiguration.java");
        String disabled = RepositoryLayout.read(
                "jc-backend/src/main/java/com/jc/backend/search/shadow/DisabledExploreSearchShadowBridge.java");
        String active = RepositoryLayout.read(
                "jc-backend/src/main/java/com/jc/backend/search/shadow/DefaultExploreSearchShadowBridge.java");
        assertThat(configuration)
                .contains("new DisabledExploreSearchShadowBridge()")
                .doesNotContain("@Profile", "@ConditionalOnProperty", "DefaultExploreSearchShadowBridge");
        assertThat(disabled).doesNotContain("SearchShadowHook", "SearchShadowExecutor", "Repository", "Logger");
        assertThat(active).doesNotContain("@Component", "@Service", "@Configuration", "@Bean");

        Path resources = RepositoryLayout.resolve("jc-backend/src/main/resources");
        try (var files = Files.walk(resources)) {
            for (Path path : files.filter(Files::isRegularFile).toList()) {
                String text = Files.readString(path);
                assertThat(text).as(RepositoryLayout.relative(path))
                        .doesNotContain("search.shadow", "search-shadow-test");
            }
        }
    }

    @Test
    void dependencyAndTaskGraphAreMinimalAndFailClosed() throws Exception {
        String build = RepositoryLayout.read("jc-backend/build.gradle.kts");
        assertThat(build).contains(
                "implementation(project(\":jc-search-shadow-wiring\"))",
                "ip9BackendHookContractTest",
                "ip9ControlledBackendHookRegression",
                "\"ip8SearchRegressionClosure\"",
                "\"ip1CompatibilityContractTest\"");
        assertThat(build).doesNotContain("ignoreFailures", "isIgnoreFailures", "ForkJoinPool");
    }

    @Test
    void allPriorRecommendationAndSqlProtectedSourcesRemainExactExceptApprovedSuccessors() throws Exception {
        Path manifest = RepositoryLayout.resolve("verification/ip8/IP8_PROTECTED_BASELINE_EXPECTED_SHA256.txt");
        List<String> lines = Files.readAllLines(manifest, StandardCharsets.UTF_8).stream()
                .filter(line -> !line.isBlank())
                .toList();
        int checked = 0;
        int approvedRca2Deltas = 0;
        int approvedPf10Deltas = 0;
        for (String line : lines) {
            String[] parts = line.trim().split("\\s+", 2);
            assertThat(parts).hasSize(2);
            Path path = RepositoryLayout.resolve(parts[1]);
            String current = sha256(path);
            if (RCA2_FEED_SERVICE.equals(parts[1])) {
                assertThat(current).as(parts[1]).isNotEqualTo(parts[0]);
                assertApprovedRca2FeedRegistrationBoundary(Files.readString(path));
                approvedRca2Deltas++;
            } else if (PF10_INTERACTION_SERVICE.equals(parts[1])) {
                assertThat(current).as(parts[1]).isNotEqualTo(parts[0]);
                assertApprovedPf10InteractionResultBoundary(Files.readString(path));
                approvedPf10Deltas++;
            } else {
                assertThat(current).as(parts[1]).isEqualTo(parts[0]);
            }
            checked++;
        }
        assertThat(lines).hasSize(320);
        assertThat(checked).isEqualTo(320);
        assertThat(approvedRca2Deltas).isEqualTo(1);
        assertThat(approvedPf10Deltas).isEqualTo(1);
    }

    @Test
    void backendProtectedDeltaAllowsApprovedControllerPf7DtoAndAdm1SecurityBoundary() throws Exception {
        Path manifest = RepositoryLayout.resolve("verification/ip9/IP9_PRECHANGE_BACKEND_PROTECTED_SHA256.txt");
        List<String> lines = Files.readAllLines(manifest, StandardCharsets.UTF_8).stream()
                .filter(line -> !line.isBlank())
                .toList();
        int approvedControllerDeltas = 0;
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
                approvedControllerDeltas++;
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
        assertThat(approvedControllerDeltas).isEqualTo(1);
        assertThat(approvedPf7DtoDeltas).isEqualTo(1);
        assertThat(approvedAdm1SecurityDeltas).isEqualTo(1);
    }

    @Test
    void canonicalSqlRemainsExact() throws Exception {
        Path manifest = RepositoryLayout.resolve("verification/ip8/IP8_SQL_01_26_EXPECTED_SHA256.txt");
        List<String> lines = Files.readAllLines(manifest, StandardCharsets.UTF_8).stream()
                .filter(line -> !line.isBlank())
                .toList();
        for (String line : lines) {
            String[] parts = line.trim().split("\\s+", 2);
            assertThat(parts).hasSize(2);
            assertThat(sha256(RepositoryLayout.resolve(parts[1]))).as(parts[1]).isEqualTo(parts[0]);
        }
        assertThat(lines).hasSize(26);
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

    private static void assertApprovedRca2FeedRegistrationBoundary(String source) {
        assertThat(source).contains(
                "ObjectProvider<Rca2RequestRegistrar>",
                "Rca2RequestRegistrar registrar = rca2Registrar.getIfAvailable();",
                "registrar.registerFeed(response, userId, tokenId, latencyMillis);",
                "catch (RuntimeException exception)",
                "RCA-2 request registration failed open",
                "return response;");
        assertThat(source).doesNotContain(
                "return registrar.registerFeed",
                "return rca2Registrar",
                "return Rca2RequestRegistrar",
                "SHADOW_RESULT_SERVING");
    }

    private static void assertApprovedPf10InteractionResultBoundary(String source) throws IOException {
        String allocation = RepositoryLayout.read(PF10_ALLOCATION);
        assertThat(allocation).contains(
                "sc-pf10-post-like-notification-v1",
                "APPROVED / IMPLEMENTATION_AUTHORITY_GRANTED",
                "SOURCE_RESULT=APPLIED",
                "RecommendationPostInteractionService",
                "PostLikeNotificationCoordinator");
        assertThat(source).contains(
                "public void apply(",
                "applyWithResult(userId, tokenId, postId, action, tracking);",
                "public Result applyWithResult(",
                "Result result = interactionStore.apply(",
                "if (result == Result.IDEMPOTENCY_CONFLICT)",
                "return result;");
        assertThat(source).doesNotContain(
                "NotificationService",
                "user_notifications",
                "post_like",
                "WebSocket",
                "SseEmitter");
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
                "SHADOW_RESULT_SERVING",
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

    private static String sha256(Path path) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
