package com.jc.backend.search.shadow.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.jc.intelligence.production.search.v1.EmptyProductionShadowCohortSelector;
import com.jc.intelligence.production.search.v1.ProductionShadowCohortSelector;
import com.jc.intelligence.production.search.v1.ProductionShadowSamplingAuthorization;
import com.jc.intelligence.production.search.v1.ProductionShadowTaskExecutor;
import com.jc.intelligence.production.search.v1.SearchShadowKillSwitch;
import com.jc.intelligence.production.search.v1.TestMutableSearchShadowKillSwitch;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

class ProductionShadowTechnicalConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ProductionSearchShadowTechnicalConfiguration.class,
                    ProductionShadowTechnicalCapabilityConfiguration.class)
            .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class));

    @Test
    void defaultContextCreatesOnlyKilledEmptyZeroCapability() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(SearchShadowKillSwitch.class);
            assertThat(context.getBean(SearchShadowKillSwitch.class).killed()).isTrue();
            assertThat(context).hasSingleBean(ProductionShadowCohortSelector.class);
            assertThat(context.getBean(ProductionShadowCohortSelector.class))
                    .isExactlyInstanceOf(EmptyProductionShadowCohortSelector.class);
            assertThat(context.getBean(ProductionShadowSamplingAuthorization.class)
                    .effectiveBasisPoints()).isZero();
            assertThat(context).doesNotHaveBean(ProductionShadowTaskExecutor.class);
            assertThat(context).doesNotHaveBean(JdbcSearchDocumentProjectionStore.class);
        });
    }

    @Test
    void explicitTechnicalTestGraphIsAvailableButProductionProfileAlwaysWins() {
        runner.withInitializer(context -> context.getEnvironment()
                        .setActiveProfiles(ProductionShadowTechnicalCapabilityCondition.PROFILE))
                .withPropertyValues(
                        ProductionShadowTechnicalCapabilityCondition.ALLOW_PROPERTY + "=true",
                        "search.shadow.production.technical.internal-key-hash="
                                + "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
                .run(context -> {
                    assertThat(context.getBean(SearchShadowKillSwitch.class))
                            .isExactlyInstanceOf(TestMutableSearchShadowKillSwitch.class);
                    assertThat(context).hasSingleBean(ProductionShadowTaskExecutor.class);
                    assertThat(context).hasSingleBean(JdbcSearchDocumentProjectionStore.class);
                });

        runner.withInitializer(context -> context.getEnvironment()
                        .setActiveProfiles("prod", ProductionShadowTechnicalCapabilityCondition.PROFILE))
                .withPropertyValues(ProductionShadowTechnicalCapabilityCondition.ALLOW_PROPERTY + "=true")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(SearchShadowKillSwitch.class);
                    assertThat(context).doesNotHaveBean(ProductionShadowSamplingAuthorization.class);
                    assertThat(context).doesNotHaveBean(ProductionShadowTaskExecutor.class);
                    assertThat(context).doesNotHaveBean(JdbcSearchDocumentProjectionStore.class);
                });
    }
}
