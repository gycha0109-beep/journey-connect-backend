package com.jc.backend.recommendation.rca2;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Locale;
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

    @Bean Rca2Op1Configuration rca2Op1Configuration(Environment environment) {
        return Rca2Op1Configuration.from(environment);
    }

    @Bean Rca2ShadowEndpointPolicy rca2ShadowEndpointPolicy(Rca2Op1Configuration configuration) {
        var policy = new Rca2ShadowEndpointPolicy(configuration.endpointAllowedHosts(),
                configuration.endpointAllowedSuffixes(), false);
        if (configuration.endpointConfigured()) {
            var decision = policy.validate(configuration.endpoint(), configuration.nonproductionNamespace(),
                    "UNRESOLVED".equals(configuration.databaseRoute()) ? "" : configuration.databaseRoute());
            if (!decision.allowed()) throw new IllegalArgumentException("OP-1 endpoint rejected: " + decision.rejection());
        }
        return policy;
    }

    @Bean Rca2WorkloadCredentialProvider rca2WorkloadCredentialProvider() {
        return Rca2WorkloadCredentialProvider.unavailable();
    }

    @Bean Rca2TestAccountAllowlist rca2TestAccountAllowlist() { return new Rca2TestAccountAllowlist(); }

    @Bean Rca2TestAccountAllowlist.Provider rca2TestAccountAllowlistProvider() {
        return Rca2TestAccountAllowlist.unavailable();
    }

    @Bean Rca2StableHashCohortSelector rca2StableHashCohortSelector(Rca2Op1Configuration configuration) {
        return new Rca2StableHashCohortSelector(configuration.cohortSaltVersion(),
                configuration.cohortSaltMaterialHash());
    }

    @Bean Rca2CandidateSourceDecision rca2CandidateSourceDecision(Rca2Op1Configuration configuration) {
        Rca2CandidateSourceDecision.Protocol protocol;
        try {
            protocol = Rca2CandidateSourceDecision.Protocol.valueOf(configuration.candidateProtocol()
                    .toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            protocol = Rca2CandidateSourceDecision.Protocol.UNRESOLVED;
        }
        if (configuration.candidateSourceResolved() && protocol == Rca2CandidateSourceDecision.Protocol.UNRESOLVED) {
            throw new IllegalArgumentException("candidate protocol is unsupported");
        }
        return new Rca2CandidateSourceDecision(configuration.candidateSource(), protocol,
                configuration.candidateApiVersion(), "INTELLIGENCE", true, true, false, false);
    }

    @Bean(destroyMethod = "close") Rca2BoundedExecutor rca2BoundedExecutor() { return new Rca2BoundedExecutor(); }
    @Bean Rca2Comparator rca2Comparator() { return new Rca2Comparator(); }
    @Bean Rca2Metrics rca2Metrics(MeterRegistry registry) { return new Rca2Metrics(registry); }
    @Bean Rca2Redaction rca2Redaction() { return new Rca2Redaction(); }
    @Bean Rca2CandidateAdapter rca2CandidateAdapter() { return Rca2CandidateAdapter.isolatedContractOnly(); }

    @Bean Rca2EnvironmentAccessGate rca2EnvironmentAccessGate(
            Rca2Op1Configuration configuration,
            Rca2ShadowEndpointPolicy endpointPolicy,
            Rca2WorkloadCredentialProvider credentialProvider,
            Rca2TestAccountAllowlist allowlist,
            Rca2TestAccountAllowlist.Provider allowlistProvider,
            Rca2StableHashCohortSelector cohortSelector,
            Rca2CandidateSourceDecision candidateSource,
            Rca2Metrics metrics) {
        return new Rca2EnvironmentAccessGate(configuration, endpointPolicy, credentialProvider, allowlist,
                allowlistProvider, cohortSelector, candidateSource, metrics, false);
    }

    @Bean Rca2RuntimeOrchestrator rca2RuntimeOrchestrator(
            Clock rca2Clock,
            Rca2FeatureFlagPolicy flags,
            Rca2KillSwitch killSwitch,
            Rca2IdentityPolicy identityPolicy,
            Rca2BoundedExecutor executor,
            Rca2CandidateAdapter adapter,
            Rca2Comparator comparator,
            Rca2Metrics metrics,
            Rca2Redaction redaction,
            Rca2EnvironmentAccessGate environmentAccessGate) {
        var breakers = new EnumMap<Rca2RuntimeContracts.Lane, Rca2CircuitBreaker>(Rca2RuntimeContracts.Lane.class);
        breakers.put(Rca2RuntimeContracts.Lane.P1, Rca2CircuitBreaker.system());
        breakers.put(Rca2RuntimeContracts.Lane.P2, Rca2CircuitBreaker.system());
        return new Rca2RuntimeOrchestrator(rca2Clock, flags, killSwitch, identityPolicy, breakers, executor,
                adapter, comparator, metrics, redaction, new Rca2Redaction.StructuredLogSink(), environmentAccessGate);
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
