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
    void allPriorRecommendationAndSqlProtectedSourcesRemainExact() throws Exception {
        Path manifest = RepositoryLayout.resolve("verification/ip8/IP8_PROTECTED_BASELINE_EXPECTED_SHA256.txt");
        List<String> lines = Files.readAllLines(manifest, StandardCharsets.UTF_8).stream()
                .filter(line -> !line.isBlank())
                .toList();
        int checked = 0;
        for (String line : lines) {
            String[] parts = line.trim().split("\\s+", 2);
            assertThat(parts).hasSize(2);
            Path path = RepositoryLayout.resolve(parts[1]);
            assertThat(sha256(path)).as(parts[1]).isEqualTo(parts[0]);
            checked++;
        }
        assertThat(lines).hasSize(320);
        assertThat(checked).isEqualTo(320);
    }

    @Test
    void backendProtectedDeltaIsLimitedToTheApprovedController() throws Exception {
        Path manifest = RepositoryLayout.resolve("verification/ip9/IP9_PRECHANGE_BACKEND_PROTECTED_SHA256.txt");
        List<String> lines = Files.readAllLines(manifest, StandardCharsets.UTF_8).stream()
                .filter(line -> !line.isBlank())
                .toList();
        int approvedDeltas = 0;
        for (String line : lines) {
            String[] parts = line.trim().split("\\s+", 2);
            assertThat(parts).hasSize(2);
            String current = sha256(RepositoryLayout.resolve(parts[1]));
            if (CONTROLLER.equals(parts[1])) {
                assertThat(current).as(parts[1]).isNotEqualTo(parts[0]);
                approvedDeltas++;
            } else {
                assertThat(current).as(parts[1]).isEqualTo(parts[0]);
            }
        }
        assertThat(approvedDeltas).isEqualTo(1);
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
