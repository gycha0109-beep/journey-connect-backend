package com.jc.backend.search.shadow.production;

import com.jc.intelligence.production.search.v1.DisabledSearchShadowKillSwitch;
import com.jc.intelligence.production.search.v1.EmptyProductionShadowCohortSelector;
import com.jc.intelligence.production.search.v1.NoOpSearchShadowEvidenceSink;
import com.jc.intelligence.production.search.v1.NoOpSearchShadowMetricSink;
import com.jc.intelligence.production.search.v1.ProductionShadowCohortSelector;
import com.jc.intelligence.production.search.v1.ProductionShadowResourcePolicyV1;
import com.jc.intelligence.production.search.v1.ProductionShadowSamplingAuthorization;
import com.jc.intelligence.production.search.v1.SearchShadowEvidenceSink;
import com.jc.intelligence.production.search.v1.SearchShadowKillSwitch;
import com.jc.intelligence.production.search.v1.SearchShadowMetricSink;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Conditional;

/** Default-killed production capability declarations. No executor, provider, runtime, or active bridge is created. */
@Configuration(proxyBeanMethods = false)
@Conditional(ProductionShadowDefaultCapabilityCondition.class)
public class ProductionSearchShadowTechnicalConfiguration {
    @Bean SearchShadowKillSwitch productionSearchShadowKillSwitch() { return new DisabledSearchShadowKillSwitch(); }
    @Bean ProductionShadowCohortSelector productionShadowCohortSelector() { return new EmptyProductionShadowCohortSelector(); }
    @Bean ProductionShadowSamplingAuthorization productionShadowSamplingAuthorization() { return ProductionShadowSamplingAuthorization.productionDefault(); }
    @Bean ProductionShadowResourcePolicyV1 productionShadowResourcePolicy() { return ProductionShadowResourcePolicyV1.provisional(); }
    @Bean SearchShadowMetricSink productionSearchShadowMetricSink() { return new NoOpSearchShadowMetricSink(); }
    @Bean SearchShadowEvidenceSink productionSearchShadowEvidenceSink() { return new NoOpSearchShadowEvidenceSink(); }
}
