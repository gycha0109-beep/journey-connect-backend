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

        var canonicalSql = Files.list(ROOT.resolve("database/journey-connect-db-v2.7"))
                .map(path -> path.getFileName().toString())
                .filter(name -> name.matches("^[0-9]{2}_.*\\.sql$"))
                .sorted()
                .toList();
        assertThat(canonicalSql).hasSize(52);
        for (int number = 1; number <= 52; number++) {
            String prefix = String.format(java.util.Locale.ROOT, "%02d_", number);
            assertThat(canonicalSql.stream().filter(name -> name.startsWith(prefix)).toList())
                    .as("canonical SQL %s must exist exactly once", prefix)
                    .hasSize(1);
        }
        assertThat(canonicalSql).contains(
                "29_data_platform_event_store.sql",
                "30_data_event_idempotency_roles.sql",
                "31_data_event_store_smoke_test.sql",
                "32_data_retry_quarantine_evidence.sql",
                "33_data_retry_processing_roles.sql",
                "34_data_retry_quarantine_smoke_test.sql",
                "35_data_recommendation_adapter_shadow_evidence.sql",
                "36_data_recommendation_adapter_shadow_persistence.sql",
                "37_data_recommendation_adapter_shadow_validation.sql",
                "38_data_projection_snapshot_foundation.sql",
                "39_data_recommendation_profile_projection.sql",
                "40_data_experiment_outcome_projection.sql",
                "41_data_projection_persistence_roles.sql",
                "42_data_projection_snapshot_validation.sql",
                "43_data_quality_validation_foundation.sql",
                "44_data_quality_metrics_and_verdict.sql",
                "45_data_quality_persistence_and_roles.sql",
                "46_data_quality_rebuild_and_safe_views.sql",
                "47_data_quality_validation.sql",
                "48_cross_track_integration_validation_foundation.sql",
                "49_cross_track_contract_mapping_and_boundary_evidence.sql",
                "50_cross_track_integration_verdict_and_conflict.sql",
                "51_cross_track_integration_persistence_roles_and_safe_view.sql",
                "52_cross_track_integration_validation.sql");
        assertThat(canonicalSql).noneMatch(name -> name.matches("^(5[3-9]|[6-9][0-9])_.*\\.sql$"));
    }
}
