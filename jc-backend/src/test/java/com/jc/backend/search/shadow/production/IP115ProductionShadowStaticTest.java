package com.jc.backend.search.shadow.production;

import static org.assertj.core.api.Assertions.assertThat;

import com.jc.intelligence.production.search.v1.SearchProductionContractIds;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class IP115ProductionShadowStaticTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void legacyAuthorityAndProductionActivationRemainProtected() throws Exception {
        String controller = Files.readString(ROOT.resolve(
                "jc-backend/src/main/java/com/jc/backend/post/PostController.java"));
        assertThat(controller).contains("postService.explore(keyword, region, pageable)");
        assertThat(controller).contains("exploreSearchShadowBridge.afterExplore");
        assertThat(controller).contains("ApiResponse.ok(legacyResponse)");
        assertThat(controller).doesNotContain("ProductionShadowTechnicalGate");

        String configuration = Files.readString(ROOT.resolve(
                "jc-backend/src/main/java/com/jc/backend/search/shadow/production/ProductionSearchShadowTechnicalConfiguration.java"));
        assertThat(configuration).contains("DisabledSearchShadowKillSwitch");
        assertThat(configuration).contains("EmptyProductionShadowCohortSelector");
        assertThat(configuration).contains("productionDefault()");
        assertThat(configuration).doesNotContain("ProductionShadowTaskExecutor productionShadowTaskExecutor");

        String migration = Files.readString(ROOT.resolve(
                "database/journey-connect-db-v2.7/27_search_document_projection.sql"));
        assertThat(migration).contains(SearchProductionContractIds.PROJECTION_SCHEMA.value());
        assertThat(migration).contains("removed_ineligible_or_missing");
        assertThat(migration).doesNotContain("CREATE TRIGGER");
    }
}
