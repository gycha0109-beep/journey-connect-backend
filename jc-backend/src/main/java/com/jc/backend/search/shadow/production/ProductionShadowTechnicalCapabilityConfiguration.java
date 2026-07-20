package com.jc.backend.search.shadow.production;

import com.jc.intelligence.production.search.v1.AllowlistedInternalCohortSelector;
import com.jc.intelligence.production.search.v1.InMemorySearchShadowEvidenceSink;
import com.jc.intelligence.production.search.v1.InMemorySearchShadowMetricSink;
import com.jc.intelligence.production.search.v1.ProductionProjectionSearchRuntimeFactory;
import com.jc.intelligence.production.search.v1.ProductionShadowCohortSelector;
import com.jc.intelligence.production.search.v1.ProductionShadowResourcePolicyV1;
import com.jc.intelligence.production.search.v1.ProductionShadowSamplingAuthorization;
import com.jc.intelligence.production.search.v1.ProductionShadowTaskExecutor;
import com.jc.intelligence.production.search.v1.ProductionShadowTechnicalGate;
import com.jc.intelligence.production.search.v1.ProjectionExploreRuntimeInputProviderFactory;
import com.jc.intelligence.production.search.v1.SearchProjectionStore;
import com.jc.intelligence.production.search.v1.SearchShadowEvidenceSink;
import com.jc.intelligence.production.search.v1.SearchShadowKillSwitch;
import com.jc.intelligence.production.search.v1.SearchShadowMetricSink;
import com.jc.intelligence.production.search.v1.TestMutableSearchShadowKillSwitch;
import java.time.Duration;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

/** Explicit technical-test graph only. It is blocked whenever prod/production is active. */
@Configuration(proxyBeanMethods = false)
@Conditional(ProductionShadowTechnicalCapabilityCondition.class)
public class ProductionShadowTechnicalCapabilityConfiguration {
    @Bean JdbcSearchDocumentProjectionStore jdbcSearchDocumentProjectionStore(org.springframework.jdbc.core.JdbcTemplate jdbc) { return new JdbcSearchDocumentProjectionStore(jdbc); }
    @Bean JdbcSearchProjectionRebuildService jdbcSearchProjectionRebuildService(org.springframework.jdbc.core.JdbcTemplate jdbc) { return new JdbcSearchProjectionRebuildService(jdbc); }
    @Bean ProjectionExploreRuntimeInputProviderFactory projectionExploreRuntimeInputProviderFactory(ProductionShadowResourcePolicyV1 policy) { return new ProjectionExploreRuntimeInputProviderFactory(policy.maximumCandidateCount()); }
    @Bean com.jc.intelligence.runtime.search.v1.SearchRuntime projectionSearchRuntime(JdbcSearchDocumentProjectionStore store) { return ProductionProjectionSearchRuntimeFactory.create(store, Duration.ofHours(24)); }
    @Bean(destroyMethod = "close") ProductionShadowTaskExecutor productionShadowTaskExecutor(ProductionShadowResourcePolicyV1 policy) { return new ProductionShadowTaskExecutor(policy); }
    @Bean @Primary TestMutableSearchShadowKillSwitch technicalTestKillSwitch() { return new TestMutableSearchShadowKillSwitch(); }
    @Bean @Primary ProductionShadowCohortSelector technicalTestCohortSelector(Environment environment) {
        String hash = environment.getProperty("search.shadow.production.technical.internal-key-hash", "");
        return new AllowlistedInternalCohortSelector(Set.of(hash), true);
    }
    @Bean @Primary ProductionShadowSamplingAuthorization technicalTestSamplingAuthorization() { return ProductionShadowSamplingAuthorization.technicalTestOnly(10_000); }
    @Bean @Primary InMemorySearchShadowMetricSink technicalTestMetricSink() { return new InMemorySearchShadowMetricSink(); }
    @Bean @Primary InMemorySearchShadowEvidenceSink technicalTestEvidenceSink() { return new InMemorySearchShadowEvidenceSink(100); }
    @Bean ProductionShadowTechnicalGate<Object> technicalProductionShadowGate(SearchShadowKillSwitch killSwitch,
            ProductionShadowCohortSelector cohort, ProductionShadowSamplingAuthorization sampling,
            ProductionShadowTaskExecutor executor, SearchShadowMetricSink metrics) {
        return new ProductionShadowTechnicalGate<>(killSwitch, cohort, sampling, executor, metrics);
    }
}
