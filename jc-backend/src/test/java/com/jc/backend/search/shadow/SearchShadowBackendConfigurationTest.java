package com.jc.backend.search.shadow;

import static org.assertj.core.api.Assertions.assertThat;

import com.jc.intelligence.integration.search.v1.SearchShadowRuntimeInputProvider;
import com.jc.intelligence.wiring.search.v1.SearchShadowComparisonLogPort;
import com.jc.intelligence.wiring.search.v1.SearchShadowExecutor;
import com.jc.intelligence.wiring.search.v1.SearchShadowHook;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SearchShadowBackendConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SearchShadowBackendConfiguration.class);

    @Test
    void missingConfigurationCreatesExactlyOneDisabledBridgeAndNoActiveInfrastructure() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(ExploreSearchShadowBridge.class);
            assertThat(context.getBean(ExploreSearchShadowBridge.class))
                    .isExactlyInstanceOf(DisabledExploreSearchShadowBridge.class);
            assertThat(context).doesNotHaveBean(SearchShadowHook.class);
            assertThat(context).doesNotHaveBean(SearchShadowExecutor.class);
            assertThat(context).doesNotHaveBean(SearchShadowRuntimeInputProvider.class);
            assertThat(context).doesNotHaveBean(SearchShadowComparisonLogPort.class);
        });
    }

    @Test
    void arbitraryPropertiesAndProductionEquivalentProfileCannotActivateShadow() {
        runner.withPropertyValues(
                        "spring.profiles.active=prod",
                        "search.shadow.mode=test_only",
                        "search.shadow.sample-basis-points=10000",
                        "search.shadow.explicit-allow=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ExploreSearchShadowBridge.class);
                    assertThat(context.getBean(ExploreSearchShadowBridge.class))
                            .isExactlyInstanceOf(DisabledExploreSearchShadowBridge.class);
                    assertThat(context).doesNotHaveBean(SearchShadowHook.class);
                    assertThat(context).doesNotHaveBean(SearchShadowExecutor.class);
                });
    }
}
