package com.jc.backend.search.shadow.production;

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
import com.jc.intelligence.production.search.v1.AllowlistedInternalCohortSelector;
import com.jc.intelligence.production.search.v1.NoOpSearchShadowEvidenceSink;
import com.jc.intelligence.production.search.v1.ProductionProjectionSearchRuntimeFactory;
import com.jc.intelligence.production.search.v1.ProductionShadowCohortSelector;
import com.jc.intelligence.production.search.v1.ProductionShadowResourcePolicyV1;
import com.jc.intelligence.production.search.v1.ProductionShadowTaskExecutor;
import com.jc.intelligence.production.search.v1.ProjectionExploreRuntimeInputProviderFactory;
import com.jc.intelligence.production.search.v1.PropertyBackedSearchShadowKillSwitch;
import com.jc.intelligence.production.search.v1.SearchShadowEvidenceSink;
import com.jc.intelligence.production.search.v1.SearchShadowKillSwitch;
import com.jc.intelligence.production.search.v1.SearchShadowMetricSink;
import com.jc.intelligence.runtime.search.v1.SearchRuntime;
import com.jc.intelligence.wiring.search.v1.SearchShadowHook;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@Conditional(ProductionSearchShadowProfileCondition.class)
@EnableConfigurationProperties(ProductionSearchShadowProperties.class)
public class ProductionSearchShadowConfiguration {
    @Bean
    ProductionSearchShadowRuntimeConfig productionSearchShadowRuntimeConfig(
            ProductionSearchShadowProperties properties) {
        return ProductionSearchShadowPropertiesValidator.validate(properties);
    }

    @Bean
    ProductionShadowResourcePolicyV1 productionPilotResourcePolicy() {
        return ProductionShadowResourcePolicyV1.approvedInitialPilot();
    }

    @Bean
    SearchShadowKillSwitch productionPropertyBackedKillSwitch(ProductionSearchShadowRuntimeConfig config) {
        return new PropertyBackedSearchShadowKillSwitch(
                () -> config.killSwitchActive() ? "disabled" : "enabled",
                config::enabled);
    }

    @Bean
    ProductionShadowCohortSelector productionInternalCohortSelector(
            ProductionSearchShadowRuntimeConfig config) {
        return new AllowlistedInternalCohortSelector(config.allowlistHashes(), true);
    }

    @Bean
    ProductionSearchShadowSamplingGate productionSearchShadowSamplingGate(
            ProductionSearchShadowRuntimeConfig config) {
        return new ProductionSearchShadowSamplingGate(config.effectiveSamplingBps());
    }

    @Bean(destroyMethod = "close")
    ProductionShadowTaskExecutor productionSearchShadowTaskExecutor(
            ProductionShadowResourcePolicyV1 policy) {
        return new ProductionShadowTaskExecutor(policy);
    }

    @Bean
    JdbcSearchDocumentProjectionStore productionSearchProjectionStore(
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        return new JdbcSearchDocumentProjectionStore(jdbcTemplate);
    }

    @Bean
    ProjectionExploreRuntimeInputProviderFactory productionExploreRuntimeInputProviderFactory(
            ProductionSearchShadowRuntimeConfig config) {
        return new ProjectionExploreRuntimeInputProviderFactory(config.maximumCandidateCount());
    }

    @Bean
    SearchRuntime productionProjectionSearchRuntime(JdbcSearchDocumentProjectionStore store) {
        return ProductionProjectionSearchRuntimeFactory.create(store, Duration.ofHours(24));
    }

    @Bean
    SearchShadowIntegrationPort<PageResponse<PostDtos.Summary>> productionSearchShadowIntegrationPort(
            ProductionSearchShadowRuntimeConfig config,
            SearchRuntime productionProjectionSearchRuntime) {
        SearchShadowPolicyV1 policy = new SearchShadowPolicyV1(
                SearchShadowMode.SHADOW_ENABLED,
                new PolicyVersion("search-shadow-production-policy-v1"),
                new PolicyVersion("search-shadow-comparison-policy-v1"),
                config.runtimeTimeout(),
                10,
                new ProducerBuildId("ip12-production-shadow-wiring"));
        return new SearchShadowIntegrationBoundary<>(
                policy,
                productionProjectionSearchRuntime,
                new DirectProductionSearchShadowExecutionPort());
    }

    @Bean
    SearchShadowMetricSink productionMicrometerSearchShadowMetricSink(MeterRegistry registry) {
        return new MicrometerSearchShadowMetricSink(registry);
    }

    @Bean
    SearchShadowEvidenceSink productionNoOpSearchShadowEvidenceSink() {
        return new NoOpSearchShadowEvidenceSink();
    }

    @Bean
    ProductionSearchShadowOperationalLogger productionSearchShadowOperationalLogger() {
        return new ProductionSearchShadowOperationalLogger();
    }

    @Bean
    ProductionInternalAccountHashResolver productionInternalAccountHashResolver() {
        return new SecurityContextProductionInternalAccountHashResolver();
    }

    @Bean
    Clock productionSearchShadowClock() {
        return Clock.systemUTC();
    }

    @Bean
    ProductionSearchShadowOperationalGate productionSearchShadowOperationalGate(
            ProductionSearchShadowRuntimeConfig config,
            SearchShadowKillSwitch killSwitch,
            ProductionShadowCohortSelector cohort,
            ProductionSearchShadowSamplingGate sampling,
            ProductionShadowTaskExecutor executor,
            SearchShadowMetricSink metrics,
            Clock productionSearchShadowClock) {
        return new ProductionSearchShadowOperationalGate(
                config, killSwitch, cohort, sampling, executor, metrics, productionSearchShadowClock);
    }

    @Bean
    SearchShadowHook<PageResponse<PostDtos.Summary>> productionExploreSearchShadowHook(
            ProductionInternalAccountHashResolver accountHashResolver,
            ProductionSearchShadowOperationalGate gate,
            SearchShadowIntegrationPort<PageResponse<PostDtos.Summary>> integrationPort,
            ProjectionExploreRuntimeInputProviderFactory providerFactory,
            SearchShadowMetricSink metrics,
            SearchShadowEvidenceSink evidenceSink,
            ProductionSearchShadowOperationalLogger logger,
            Clock productionSearchShadowClock) {
        return new ProductionExploreSearchShadowHook(
                accountHashResolver,
                gate,
                integrationPort,
                providerFactory,
                metrics,
                evidenceSink,
                logger,
                productionSearchShadowClock);
    }

    @Bean
    ExploreShadowRequestContextProvider productionExploreShadowRequestContextProvider(
            Clock productionSearchShadowClock) {
        return new ProductionExploreShadowRequestContextProvider(productionSearchShadowClock);
    }

    @Bean
    ExploreShadowHookRequestFactory productionExploreShadowHookRequestFactory(
            ExploreShadowRequestContextProvider contextProvider) {
        return new DefaultExploreShadowHookRequestFactory(contextProvider);
    }

    @Bean
    ExploreSearchShadowBridge productionExploreSearchShadowBridge(
            ExploreShadowHookRequestFactory requestFactory,
            SearchShadowHook<PageResponse<PostDtos.Summary>> productionExploreSearchShadowHook) {
        return new DefaultExploreSearchShadowBridge(requestFactory, productionExploreSearchShadowHook);
    }

    @Bean
    ApplicationRunner productionSearchShadowStartupReporter(
            ProductionSearchShadowRuntimeConfig config,
            ProductionSearchShadowOperationalLogger logger) {
        return ignored -> logger.startup(config);
    }
}
