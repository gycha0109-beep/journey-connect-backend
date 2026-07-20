package com.jc.backend.search.shadow;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Conditional;

/**
 * Production-equivalent configuration. It deliberately exposes only the disabled no-op bridge.
 * No property, profile, executor, runtime input provider, or active Search hook is bound here.
 */
@Configuration(proxyBeanMethods = false)
public class SearchShadowBackendConfiguration {
    @Bean
    @Conditional(DisabledSearchShadowActivationCondition.class)
    @ConditionalOnMissingBean(ExploreSearchShadowBridge.class)
    ExploreSearchShadowBridge exploreSearchShadowBridge() {
        return new DisabledExploreSearchShadowBridge();
    }
}
