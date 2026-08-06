package com.jc.backend.intelligence.search;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SearchCtrManualActivationProperties.class)
public class SearchCtrManualActivationConfiguration {

    @Bean
    SearchCtrManualActivationGate searchCtrManualActivationGate() {
        return SearchCtrManualActivationGate.current();
    }

    @Bean(name = "searchCtrManualClock")
    @ConditionalOnProperty(
            prefix = "app.intelligence.search-ctr.manual",
            name = "enabled",
            havingValue = "true")
    Clock searchCtrManualClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "app.intelligence.search-ctr.manual",
            name = "enabled",
            havingValue = "true")
    SearchCtrManualActivationRunner searchCtrManualActivationRunner(
            SearchCtrManualActivationProperties properties,
            SearchCtrManualActivationGate gate,
            SearchCtrManualActivationPort port,
            Environment environment,
            @Qualifier("searchCtrManualClock") Clock clock,
            @Value("${app.database.role-routing.require-reliability:false}")
                    boolean reliabilityCapabilityRequired) {
        return new SearchCtrManualActivationRunner(
                properties,
                gate,
                port,
                environment,
                clock,
                reliabilityCapabilityRequired);
    }
}
