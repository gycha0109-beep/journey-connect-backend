package com.jc.backend.search.shadow.production;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class IP12ProductionShadowStaticTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void productionDefaultsCeilingPrivacyAndLegacyAuthorityAreProtected() throws Exception {
        String controller = Files.readString(ROOT.resolve(
                "jc-backend/src/main/java/com/jc/backend/post/PostController.java"));
        assertThat(controller).contains("postService.explore(keyword, region, pageable)");
        assertThat(controller).contains("exploreSearchShadowBridge.afterExplore");
        assertThat(controller).contains("ApiResponse.ok(legacyResponse)");
        assertThat(controller).doesNotContain("ProductionSearchShadowProperties");

        String properties = Files.readString(ROOT.resolve(
                "jc-backend/src/main/java/com/jc/backend/search/shadow/production/ProductionSearchShadowProperties.java"));
        assertThat(properties).contains("APPROVED_MAXIMUM_SAMPLING_BPS = 10");
        assertThat(properties).contains("private boolean killSwitch = true");
        assertThat(properties).contains("private int samplingBps");

        String profile = Files.readString(ROOT.resolve(
                "jc-backend/src/main/resources/application-prod.yml"));
        assertThat(profile).contains("enabled: ${JC_SEARCH_SHADOW_PRODUCTION_ENABLED:false}");
        assertThat(profile).contains("kill-switch: ${JC_SEARCH_SHADOW_PRODUCTION_KILL_SWITCH:true}");
        assertThat(profile).contains("sampling-bps: ${JC_SEARCH_SHADOW_PRODUCTION_SAMPLING_BPS:0}");
        assertThat(profile).contains("max-approved-sampling-bps: 10");
        assertThat(profile).contains("activation-approval-ref: ${JC_SEARCH_SHADOW_PRODUCTION_APPROVAL_REF:}");
        assertThat(profile).contains("rollback-owner-ref: ${JC_SEARCH_SHADOW_PRODUCTION_ROLLBACK_OWNER_REF:}");
        assertThat(profile).contains("metric-verification-ref: ${JC_SEARCH_SHADOW_PRODUCTION_METRIC_REF:}");
        assertThat(profile).doesNotContain("0123456789abcdef");

        String source = Files.walk(ROOT.resolve(
                        "jc-backend/src/main/java/com/jc/backend/search/shadow/production"))
                .filter(path -> path.toString().endsWith(".java"))
                .map(path -> {
                    try { return Files.readString(path); }
                    catch (java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
                })
                .collect(Collectors.joining("\n"));
        assertThat(source).doesNotContain("legacyResponse.items().stream().map");
        assertThat(source).doesNotContain("logger.info(keyword");
        assertThat(source).doesNotContain("account_hash\"");
        assertThat(source).doesNotContain("Search output response");

        assertThat(Files.list(ROOT.resolve("database/journey-connect-db-v2.7"))
                .filter(path -> path.getFileName().toString().matches("^[0-9]{2}_.*\\.sql$"))
                .count()).isEqualTo(28L);
    }
}
