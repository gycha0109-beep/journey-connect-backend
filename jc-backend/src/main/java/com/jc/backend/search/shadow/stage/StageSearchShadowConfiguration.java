package com.jc.backend.search.shadow.stage;

import com.jc.backend.common.PageResponse;
import com.jc.backend.post.PostDtos;
import com.jc.backend.search.shadow.DefaultExploreSearchShadowBridge;
import com.jc.backend.search.shadow.DefaultExploreShadowHookRequestFactory;
import com.jc.backend.search.shadow.ExploreSearchShadowBridge;
import com.jc.backend.search.shadow.ExploreShadowHookRequestFactory;
import com.jc.backend.search.shadow.ExploreShadowRequestContextProvider;
import com.jc.intelligence.contract.v1.version.PolicyVersion;
import com.jc.intelligence.contract.v1.version.ProducerBuildId;
import com.jc.intelligence.integration.search.v1.SearchShadowIntegrationBoundary;
import com.jc.intelligence.integration.search.v1.SearchShadowIntegrationPort;
import com.jc.intelligence.integration.search.v1.SearchShadowMode;
import com.jc.intelligence.integration.search.v1.SearchShadowPolicyV1;
import com.jc.intelligence.runtime.search.v1.DefaultSearchRuntime;
import com.jc.intelligence.runtime.search.v1.SearchRuntime;
import com.jc.intelligence.runtime.search.v1.fixture.PassThroughSearchCandidateFilter;
import com.jc.intelligence.runtime.search.v1.port.SearchDependencyDecision;
import com.jc.intelligence.runtime.search.v1.ranking.NoOpSearchRerankingPort;
import com.jc.intelligence.wiring.search.v1.FixedSearchShadowCircuitBreaker;
import com.jc.intelligence.wiring.search.v1.SearchShadowCircuitBreaker;
import com.jc.intelligence.wiring.search.v1.SearchShadowCircuitState;
import com.jc.intelligence.wiring.search.v1.SearchShadowComparisonLogPort;
import com.jc.intelligence.wiring.search.v1.SearchShadowHook;
import com.jc.intelligence.wiring.search.v1.SearchShadowWiringConfigV1;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** Explicit test/stage assembly. It is absent from default and production-equivalent contexts. */
@Configuration(proxyBeanMethods = false)
@Conditional(StageSearchShadowActivationCondition.class)
public class StageSearchShadowConfiguration {
    @Bean StageSearchShadowProperties stageSearchShadowProperties(Environment environment) {
        StageSearchShadowProperties properties = StageSearchShadowProperties.from(environment);
        if (properties == null) throw new IllegalStateException("stage Search shadow activation condition drift");
        return properties;
    }

    @Bean SearchShadowWiringConfigV1 stageSearchShadowWiringConfig(StageSearchShadowProperties properties) {
        return properties.wiringConfig();
    }

    @Bean InMemoryStageSearchCatalog inMemoryStageSearchCatalog() {
        return new InMemoryStageSearchCatalog();
    }

    @Bean SearchRuntime stageSearchRuntime(InMemoryStageSearchCatalog catalog) {
        return new DefaultSearchRuntime(catalog, new PassThroughSearchCandidateFilter(),
                (request, candidate) -> SearchDependencyDecision.ALLOW,
                (request, candidate) -> SearchDependencyDecision.ALLOW,
                catalog, new NoOpSearchRerankingPort());
    }

    @Bean(destroyMethod = "close") StageBoundedSearchShadowExecutionPort stageSearchShadowExecutionPort(
            StageSearchShadowProperties properties) {
        return new StageBoundedSearchShadowExecutionPort(properties.maxConcurrency(), properties.queueCapacity());
    }

    @Bean SearchShadowIntegrationPort<PageResponse<PostDtos.Summary>> stageSearchShadowIntegrationPort(
            StageSearchShadowProperties properties,
            SearchRuntime stageSearchRuntime,
            StageBoundedSearchShadowExecutionPort stageSearchShadowExecutionPort) {
        SearchShadowPolicyV1 policy = new SearchShadowPolicyV1(SearchShadowMode.TEST_ONLY,
                new PolicyVersion("search-shadow-policy-v1"),
                new PolicyVersion("search-shadow-comparison-policy-v1"), properties.timeout(), properties.topK(),
                new ProducerBuildId("ip10-test-stage-shadow"));
        return new SearchShadowIntegrationBoundary<>(policy, stageSearchRuntime, stageSearchShadowExecutionPort);
    }

    @Bean StageExploreSearchRuntimeInputProviderFactory stageExploreSearchRuntimeInputProviderFactory(
            InMemoryStageSearchCatalog catalog) {
        return new DefaultStageExploreSearchRuntimeInputProviderFactory(Math.max(100, catalog.size()));
    }

    @Bean(destroyMethod = "close") StageSearchShadowTaskExecutor stageSearchShadowTaskExecutor(
            StageSearchShadowProperties properties) {
        return new StageSearchShadowTaskExecutor(properties.maxConcurrency(), properties.queueCapacity());
    }

    @Bean InMemoryStageSearchShadowComparisonLogPort inMemoryStageSearchShadowComparisonLogPort() {
        return new InMemoryStageSearchShadowComparisonLogPort(1_000);
    }


    @Bean SearchShadowCircuitBreaker stageSearchShadowCircuitBreaker() {
        return new FixedSearchShadowCircuitBreaker(SearchShadowCircuitState.CLOSED, false);
    }

    @Bean SearchShadowHook<PageResponse<PostDtos.Summary>> stageExploreSearchShadowHook(
            SearchShadowWiringConfigV1 config,
            StageSearchShadowTaskExecutor taskExecutor,
            SearchShadowCircuitBreaker circuitBreaker,
            SearchShadowIntegrationPort<PageResponse<PostDtos.Summary>> integrationPort,
            StageExploreSearchRuntimeInputProviderFactory providerFactory,
            SearchShadowComparisonLogPort logPort) {
        return new StageExploreSearchShadowHook(config, taskExecutor, circuitBreaker, integrationPort,
                providerFactory, logPort);
    }

    @Bean ExploreShadowRequestContextProvider stageExploreShadowRequestContextProvider() {
        return new StageExploreShadowRequestContextProvider(Clock.systemUTC());
    }

    @Bean ExploreShadowHookRequestFactory stageExploreShadowHookRequestFactory(
            ExploreShadowRequestContextProvider contextProvider) {
        return new DefaultExploreShadowHookRequestFactory(contextProvider);
    }

    @Bean ExploreSearchShadowBridge stageExploreSearchShadowBridge(
            ExploreShadowHookRequestFactory requestFactory,
            SearchShadowHook<PageResponse<PostDtos.Summary>> stageExploreSearchShadowHook) {
        return new DefaultExploreSearchShadowBridge(requestFactory, stageExploreSearchShadowHook);
    }
}
