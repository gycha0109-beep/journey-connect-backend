package com.jc.backend.search.shadow.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.jc.backend.search.shadow.DisabledExploreSearchShadowBridge;
import com.jc.backend.search.shadow.ExploreSearchShadowBridge;
import com.jc.backend.search.shadow.SearchShadowBackendConfiguration;
import com.jc.backend.search.shadow.stage.StageSearchShadowConfiguration;
import com.jc.intelligence.production.search.v1.ProductionShadowTaskExecutor;
import com.jc.intelligence.production.search.v1.SearchShadowKillSwitch;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

class ProductionSearchShadowConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(
                    SearchShadowBackendConfiguration.class,
                    ProductionSearchShadowTechnicalConfiguration.class,
                    ProductionSearchShadowConfiguration.class,
                    StageSearchShadowConfiguration.class)
            .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new);

    @Test
    void defaultProfileRemainsDisabledNoOp() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(ExploreSearchShadowBridge.class);
            assertThat(context.getBean(ExploreSearchShadowBridge.class))
                    .isExactlyInstanceOf(DisabledExploreSearchShadowBridge.class);
            assertThat(context).doesNotHaveBean(ProductionSearchShadowRuntimeConfig.class);
        });
    }

    @Test
    void productionProfileBindsOperationalGraphButDefaultsRemainKilledZeroAndEmpty() {
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .run(context -> {
                    assertThat(context).hasSingleBean(ExploreSearchShadowBridge.class);
                    assertThat(context.getBean(ExploreSearchShadowBridge.class))
                            .isNotInstanceOf(DisabledExploreSearchShadowBridge.class);
                    var config = context.getBean(ProductionSearchShadowRuntimeConfig.class);
                    assertThat(config.enabled()).isFalse();
                    assertThat(config.killSwitchActive()).isTrue();
                    assertThat(config.effectiveSamplingBps()).isZero();
                    assertThat(config.allowlistHashes()).isEmpty();
                    assertThat(config.operationalInputsPresent()).isFalse();
                    assertThat(context.getBean(SearchShadowKillSwitch.class).killed()).isTrue();
                    assertThat(context).hasSingleBean(ProductionShadowTaskExecutor.class);
                });
    }

    @Test
    void productionAndStageProfileConflictSelectsProductionFailClosedGraphOnly() {
        runner.withInitializer(context -> context.getEnvironment()
                        .setActiveProfiles("prod", "search-shadow-stage"))
                .withPropertyValues(
                        "search.shadow.stage.mode=test_only",
                        "search.shadow.stage.explicit-allow=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ExploreSearchShadowBridge.class);
                    assertThat(context).hasSingleBean(ProductionSearchShadowRuntimeConfig.class);
                    assertThat(context).doesNotHaveBean(
                            com.jc.backend.search.shadow.stage.StageSearchShadowProperties.class);
                });
    }

    @Test
    void positiveSamplingWithoutOperationalApprovalOrAllowlistFailsStartup() {
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .withPropertyValues(
                        "app.intelligence.search-shadow.production.enabled=true",
                        "app.intelligence.search-shadow.production.kill-switch=false",
                        "app.intelligence.search-shadow.production.sampling-bps=10")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void elevenBasisPointsFailsStartup() {
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .withPropertyValues(
                        "app.intelligence.search-shadow.production.enabled=true",
                        "app.intelligence.search-shadow.production.kill-switch=false",
                        "app.intelligence.search-shadow.production.sampling-bps=11")
                .run(context -> assertThat(context).hasFailed());
    }
}
