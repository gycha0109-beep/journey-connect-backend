package com.jc.backend.recommendation.rca2;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.EnumMap;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@Profile("rca2-isolated-nonproduction & !prod & !production")
public class Rca2SpringConfiguration {
    @Bean Clock rca2Clock() { return Clock.systemUTC(); }

    @Bean Rca2FeatureFlagPolicy rca2FeatureFlagPolicy(Clock rca2Clock) {
        return new Rca2FeatureFlagPolicy(Rca2FeatureFlagPolicy.Snapshot.defaultOff(rca2Clock.instant()));
    }

    @Bean(destroyMethod = "close") Rca2FlagRefreshService rca2FlagRefreshService(
            Environment environment, Rca2FeatureFlagPolicy policy, Clock rca2Clock) {
        return new Rca2FlagRefreshService(environment, policy, rca2Clock);
    }

    @Bean Rca2KillSwitch rca2KillSwitch(Environment environment) {
        var killSwitch = new Rca2KillSwitch();
        String value = environment.getProperty("app.recommendation.rca2.global-kill-switch");
        if (!"false".equals(value)) killSwitch.killGlobal();
        return killSwitch;
    }

    @Bean Rca2IdentityPolicy rca2IdentityPolicy(Environment environment) {
        String raw = environment.getProperty("app.recommendation.rca2.test-account-hashes", "");
        Set<String> hashes = raw.isBlank() ? Set.of() : Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> value.matches("[0-9a-f]{64}"))
                .collect(Collectors.toUnmodifiableSet());
        return new Rca2IdentityPolicy(hashes, Set.of("rca2-post-response-hook", "rca2-contract-test"));
    }

    @Bean(destroyMethod = "close") Rca2BoundedExecutor rca2BoundedExecutor() { return new Rca2BoundedExecutor(); }
    @Bean Rca2Comparator rca2Comparator() { return new Rca2Comparator(); }
    @Bean Rca2Metrics rca2Metrics(MeterRegistry registry) { return new Rca2Metrics(registry); }
    @Bean Rca2Redaction rca2Redaction() { return new Rca2Redaction(); }
    @Bean Rca2CandidateAdapter rca2CandidateAdapter() { return Rca2CandidateAdapter.isolatedContractOnly(); }

    @Bean Rca2RuntimeOrchestrator rca2RuntimeOrchestrator(
            Clock rca2Clock,
            Rca2FeatureFlagPolicy flags,
            Rca2KillSwitch killSwitch,
            Rca2IdentityPolicy identityPolicy,
            Rca2BoundedExecutor executor,
            Rca2CandidateAdapter adapter,
            Rca2Comparator comparator,
            Rca2Metrics metrics,
            Rca2Redaction redaction) {
        var breakers = new EnumMap<Rca2RuntimeContracts.Lane, Rca2CircuitBreaker>(Rca2RuntimeContracts.Lane.class);
        breakers.put(Rca2RuntimeContracts.Lane.P1, Rca2CircuitBreaker.system());
        breakers.put(Rca2RuntimeContracts.Lane.P2, Rca2CircuitBreaker.system());
        return new Rca2RuntimeOrchestrator(rca2Clock, flags, killSwitch, identityPolicy, breakers, executor,
                adapter, comparator, metrics, redaction, new Rca2Redaction.StructuredLogSink());
    }

    @Bean Rca2RequestRegistrar rca2RequestRegistrar(
            ObjectMapper objectMapper, Clock rca2Clock, Rca2IdentityPolicy identityPolicy) {
        return new Rca2RequestRegistrar(objectMapper, rca2Clock, identityPolicy);
    }

    @Bean FilterRegistrationBean<Rca2PostResponseFilter> rca2PostResponseFilter(
            Rca2RequestRegistrar registrar, Rca2RuntimeOrchestrator orchestrator) {
        var registration = new FilterRegistrationBean<>(new Rca2PostResponseFilter(registrar, orchestrator));
        registration.setName("rca2PostResponseFilter");
        registration.setOrder(Ordered.LOWEST_PRECEDENCE);
        registration.addUrlPatterns("/api/v1/feed");
        return registration;
    }
}
