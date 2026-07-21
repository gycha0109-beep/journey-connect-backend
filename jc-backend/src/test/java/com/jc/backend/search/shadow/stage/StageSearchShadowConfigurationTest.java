package com.jc.backend.search.shadow.stage;

import static org.assertj.core.api.Assertions.assertThat;

import com.jc.backend.search.shadow.DisabledExploreSearchShadowBridge;
import com.jc.backend.search.shadow.ExploreSearchShadowBridge;
import com.jc.backend.search.shadow.SearchShadowBackendConfiguration;
import com.jc.intelligence.wiring.search.v1.SearchShadowHook;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class StageSearchShadowConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SearchShadowBackendConfiguration.class, StageSearchShadowConfiguration.class);

    @Test
    void defaultAndMissingConfigurationRemainFullyDisabled() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(ExploreSearchShadowBridge.class);
            assertThat(context.getBean(ExploreSearchShadowBridge.class))
                    .isExactlyInstanceOf(DisabledExploreSearchShadowBridge.class);
            assertThat(context).doesNotHaveBean(StageSearchShadowTaskExecutor.class);
            assertThat(context).doesNotHaveBean(StageBoundedSearchShadowExecutionPort.class);
            assertThat(context).doesNotHaveBean(InMemoryStageSearchCatalog.class);
            assertThat(context).doesNotHaveBean(SearchShadowHook.class);
        });
    }

    @Test
    void explicitTestProfileCreatesOneActiveGraph() {
        activeRunner(SearchShadowWiringProfile.TEST).run(context -> {
            assertThat(context).hasSingleBean(ExploreSearchShadowBridge.class);
            assertThat(context.getBean(ExploreSearchShadowBridge.class))
                    .isNotInstanceOf(DisabledExploreSearchShadowBridge.class);
            assertThat(context).hasSingleBean(StageSearchShadowTaskExecutor.class);
            assertThat(context).hasSingleBean(StageBoundedSearchShadowExecutionPort.class);
            assertThat(context).hasSingleBean(InMemoryStageSearchCatalog.class);
            assertThat(context).hasSingleBean(InMemoryStageSearchShadowComparisonLogPort.class);
            assertThat(context).hasSingleBean(SearchShadowHook.class);
            assertThat(context.getBean(StageSearchShadowProperties.class).sampleBasisPoints()).isEqualTo(10_000);
        });
    }

    @Test
    void explicitStageProfileCreatesOneActiveGraph() {
        activeRunner(SearchShadowWiringProfile.STAGE).run(context -> {
            assertThat(context).hasSingleBean(ExploreSearchShadowBridge.class);
            assertThat(context).hasSingleBean(StageSearchShadowTaskExecutor.class);
            assertThat(context.getBean(StageSearchShadowProperties.class).activeProfile())
                    .isEqualTo("search-shadow-stage");
        });
    }

    @Test
    void productionProfileDelegatesBridgeOwnershipAndInvalidStagePropertiesFailClosed() {
        runner.withInitializer(context -> context.getEnvironment()
                        .setActiveProfiles("prod", "search-shadow-stage"))
                .withPropertyValues(activeProperties())
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ExploreSearchShadowBridge.class);
                    assertThat(context).doesNotHaveBean(StageSearchShadowTaskExecutor.class);
                });

        runner.withInitializer(context -> context.getEnvironment()
                        .setActiveProfiles("production", "search-shadow-stage"))
                .withPropertyValues(activeProperties())
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ExploreSearchShadowBridge.class);
                    assertThat(context).doesNotHaveBean(StageSearchShadowTaskExecutor.class);
                });

        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("search-shadow-test"))
                .withPropertyValues(
                        "search.shadow.stage.explicit-allow=true",
                        "search.shadow.stage.mode=unknown",
                        "search.shadow.stage.sample-basis-points=10000")
                .run(context -> {
                    assertThat(context).hasSingleBean(ExploreSearchShadowBridge.class);
                    assertThat(context.getBean(ExploreSearchShadowBridge.class))
                            .isExactlyInstanceOf(DisabledExploreSearchShadowBridge.class);
                });

        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("search-shadow-test"))
                .withPropertyValues(
                        "search.shadow.stage.explicit-allow=not-a-boolean",
                        "search.shadow.stage.mode=test_only")
                .run(context -> {
                    assertThat(context).hasSingleBean(ExploreSearchShadowBridge.class);
                    assertThat(context.getBean(ExploreSearchShadowBridge.class))
                            .isExactlyInstanceOf(DisabledExploreSearchShadowBridge.class);
                    assertThat(context).doesNotHaveBean(StageSearchShadowTaskExecutor.class);
                });
    }

    private ApplicationContextRunner activeRunner(SearchShadowWiringProfile profile) {
        return runner.withInitializer(context -> context.getEnvironment().setActiveProfiles(profile.value))
                .withPropertyValues(activeProperties());
    }

    private static String[] activeProperties() {
        return new String[] {
                "search.shadow.stage.explicit-allow=true",
                "search.shadow.stage.mode=test_only",
                "search.shadow.stage.sample-basis-points=10000",
                "search.shadow.stage.timeout-millis=200",
                "search.shadow.stage.queue-capacity=8",
                "search.shadow.stage.max-concurrency=2",
                "search.shadow.stage.top-k=10"
        };
    }

    private enum SearchShadowWiringProfile {
        TEST("search-shadow-test"), STAGE("search-shadow-stage");
        private final String value;
        SearchShadowWiringProfile(String value) { this.value = value; }
    }
}
