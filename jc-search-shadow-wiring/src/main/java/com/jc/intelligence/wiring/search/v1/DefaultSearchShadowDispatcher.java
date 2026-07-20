package com.jc.intelligence.wiring.search.v1;

import com.jc.intelligence.compat.search.explore.v1.LegacyExploreCompatibilityResult;
import com.jc.intelligence.integration.search.v1.SearchShadowComparisonEvidenceV1;
import com.jc.intelligence.integration.search.v1.SearchShadowComparisonStatus;
import com.jc.intelligence.integration.search.v1.SearchShadowContextV1;
import com.jc.intelligence.integration.search.v1.SearchShadowFingerprintV1;
import com.jc.intelligence.integration.search.v1.SearchShadowIntegrationPort;
import com.jc.intelligence.integration.search.v1.SearchShadowIntegrationResult;
import com.jc.intelligence.integration.search.v1.SearchShadowRuntimeInputProvider;
import com.jc.intelligence.integration.search.v1.SearchShadowSeverity;
import com.jc.intelligence.integration.search.v1.SearchShadowStatus;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

public final class DefaultSearchShadowDispatcher<T> implements SearchShadowDispatcher<T> {
    private final SearchShadowWiringConfigV1 config;
    private final SearchShadowActivationGate activationGate;
    private final SearchShadowSampler sampler;
    private final SearchShadowExecutor executor;
    private final SearchShadowCircuitBreaker circuitBreaker;
    private final SearchShadowIntegrationPort<T> integrationPort;
    private final SearchShadowRuntimeInputProvider runtimeInputProvider;
    private final SearchShadowComparisonLogPort logPort;

    public DefaultSearchShadowDispatcher(
            SearchShadowWiringConfigV1 config,
            SearchShadowExecutor executor,
            SearchShadowCircuitBreaker circuitBreaker,
            SearchShadowIntegrationPort<T> integrationPort,
            SearchShadowRuntimeInputProvider runtimeInputProvider,
            SearchShadowComparisonLogPort logPort) {
        this.config = config == null ? SearchShadowWiringConfigV1.disabledByDefault(
                new com.jc.intelligence.contract.v1.version.ProducerBuildId("ip7-disabled-default")) : config;
        this.activationGate = new SearchShadowActivationGate();
        this.sampler = new DeterministicSearchShadowSampler();
        this.executor = Objects.requireNonNull(executor, "executor");
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker, "circuitBreaker");
        this.integrationPort = Objects.requireNonNull(integrationPort, "integrationPort");
        this.runtimeInputProvider = Objects.requireNonNull(runtimeInputProvider, "runtimeInputProvider");
        this.logPort = Objects.requireNonNull(logPort, "logPort");
    }

    @Override public SearchShadowDispatchReceiptV1<T> dispatch(
            T legacyResponse, LegacyExploreCompatibilityResult legacyCompatibility, SearchShadowContextV1 context) {
        Objects.requireNonNull(legacyResponse, "legacyResponse");
        Objects.requireNonNull(legacyCompatibility, "legacyCompatibility");
        Objects.requireNonNull(context, "context");
        SearchShadowActivationDecisionV1 activation = activationGate.decide(config,
                new SearchShadowActivationInputsV1(true, executor.available() && executor.queueCapacity() >= config.queueCapacity()
                        && executor.maxConcurrency() >= config.maxConcurrency(), logPort.available()));
        if (!activation.activated()) {
            SearchShadowDispatchStatus status = "profile_blocked".equals(activation.reasonCode())
                    ? SearchShadowDispatchStatus.PROFILE_BLOCKED : SearchShadowDispatchStatus.DISABLED;
            return receipt(legacyResponse, status, null, null, null, null, activation.reasonCode());
        }
        SearchShadowSamplingDecisionV1 sampling = sampler.decide(context.correlationId(),
                new SearchShadowSamplingPolicyV1(config.sampleBasisPoints(), config.samplingPolicyVersion()));
        if (!sampling.included()) return receipt(legacyResponse, SearchShadowDispatchStatus.NOT_SAMPLED,
                sampling, null, null, null, "not_sampled");
        String correlationFingerprint = SearchShadowFingerprintV1.sha256(context.correlationId());
        SearchShadowCircuitDecisionV1 circuit = circuitBreaker.evaluate(correlationFingerprint);
        if (!circuit.permitted()) return receipt(legacyResponse, SearchShadowDispatchStatus.CIRCUIT_OPEN,
                sampling, circuit, null, null, circuit.reasonCode());
        SearchShadowExecutorResultV1<SearchShadowIntegrationResult<T>> execution;
        try {
            execution = Objects.requireNonNull(executor.submit(
                    () -> integrationPort.integrate(legacyResponse, legacyCompatibility, context, runtimeInputProvider),
                    config.timeout()), "executor returned null");
        } catch (RuntimeException exception) {
            return receipt(legacyResponse, SearchShadowDispatchStatus.FAILED, sampling, circuit, null, null, "executor_failed");
        }
        if (execution.status() != SearchShadowExecutorStatus.COMPLETED) {
            return receipt(legacyResponse, map(execution.status()), sampling, circuit, null, null,
                    execution.safeFailureCode() == null ? execution.status().wireValue() : execution.safeFailureCode());
        }
        SearchShadowIntegrationResult<T> integration = execution.value();
        if (integration.legacyResponse() != legacyResponse) {
            return receipt(legacyResponse, SearchShadowDispatchStatus.FAILED, sampling, circuit, null, null, "response_identity_violation");
        }
        SearchShadowDispatchStatus integrationDispatchStatus = map(integration.shadowStatus());
        SearchShadowStructuredRecordV1 record = structuredRecord(correlationFingerprint, sampling, integration, context, integrationDispatchStatus);
        SearchShadowComparisonLogResultV1 logResult;
        try {
            logResult = Objects.requireNonNull(logPort.log(record), "log port returned null");
        } catch (RuntimeException exception) {
            return receipt(legacyResponse, SearchShadowDispatchStatus.LOGGING_FAILED, sampling, circuit, integration,
                    new SearchShadowComparisonLogResultV1(SearchShadowLogStatus.FAILED, "logging_failed"), "logging_failed");
        }
        SearchShadowDispatchStatus status = integrationDispatchStatus;
        if (logResult.status() == SearchShadowLogStatus.FAILED) status = SearchShadowDispatchStatus.LOGGING_FAILED;
        return receipt(legacyResponse, status, sampling, circuit, integration, logResult,
                logResult.safeCode() == null ? status.wireValue() : logResult.safeCode());
    }

    private SearchShadowStructuredRecordV1 structuredRecord(
            String correlationFingerprint, SearchShadowSamplingDecisionV1 sampling,
            SearchShadowIntegrationResult<T> integration, SearchShadowContextV1 context,
            SearchShadowDispatchStatus dispatchStatus) {
        SearchShadowComparisonEvidenceV1 evidence = integration.comparisonEvidence();
        if (evidence == null) {
            return new SearchShadowStructuredRecordV1(SearchShadowWiringContractIds.STRUCTURED_RECORD,
                    correlationFingerprint, config.mode(), sampling.included(), dispatchStatus,
                    integration.shadowStatus(), null, List.of(), List.of(), SearchShadowSeverity.INFO, 0, 0, 1.0d,
                    "not_recorded", "not_recorded", config.samplingPolicyVersion().value(),
                    "search-shadow-policy-v1", config.producerBuildId(), context.referenceTime(), SearchShadowWiringAuthorityV1.legacyOnly());
        }
        Duration runtime = evidence.metrics().runtimeDuration();
        Duration comparison = evidence.metrics().comparisonDuration();
        return new SearchShadowStructuredRecordV1(SearchShadowWiringContractIds.STRUCTURED_RECORD,
                correlationFingerprint, config.mode(), sampling.included(), dispatchStatus,
                integration.shadowStatus(), evidence.comparisonStatus(),
                evidence.mismatches().stream().map(m -> m.code()).distinct().sorted().toList(),
                evidence.warningCodes().stream().distinct().sorted().toList(), evidence.severity(), evidence.metrics().legacyCount(), evidence.metrics().runtimeCount(),
                evidence.metrics().topKOverlapRatio(), durationBucket(runtime), durationBucket(comparison),
                config.samplingPolicyVersion().value(), evidence.shadowPolicyVersion().value(), config.producerBuildId(),
                context.referenceTime(), SearchShadowWiringAuthorityV1.legacyOnly());
    }

    private static String durationBucket(Duration duration) {
        long millis = duration.toMillis();
        if (millis == 0) return "zero";
        if (millis <= 10) return "up_to_10ms";
        if (millis <= 50) return "up_to_50ms";
        if (millis <= 100) return "up_to_100ms";
        return "over_100ms";
    }

    private static SearchShadowDispatchStatus map(SearchShadowExecutorStatus status) {
        return switch (status) {
            case REJECTED -> SearchShadowDispatchStatus.REJECTED;
            case QUEUE_FULL -> SearchShadowDispatchStatus.QUEUE_FULL;
            case EXECUTOR_UNAVAILABLE -> SearchShadowDispatchStatus.EXECUTOR_UNAVAILABLE;
            case TIMED_OUT -> SearchShadowDispatchStatus.TIMED_OUT;
            case CANCELLED -> SearchShadowDispatchStatus.CANCELLED;
            case FAILED -> SearchShadowDispatchStatus.FAILED;
            case COMPLETED -> throw new IllegalArgumentException("completed status cannot be mapped as failure");
        };
    }

    private static SearchShadowDispatchStatus map(SearchShadowStatus status) {
        return switch (status) {
            case DISABLED -> SearchShadowDispatchStatus.DISABLED;
            case COMPARED, NOT_COMPARABLE -> SearchShadowDispatchStatus.COMPLETED;
            case INPUT_UNAVAILABLE -> SearchShadowDispatchStatus.INPUT_UNAVAILABLE;
            case INPUT_UNSUPPORTED -> SearchShadowDispatchStatus.INPUT_UNSUPPORTED;
            case INVALID_INPUT -> SearchShadowDispatchStatus.INVALID_INPUT;
            case TIMED_OUT -> SearchShadowDispatchStatus.TIMED_OUT;
            case RUNTIME_FAILED -> SearchShadowDispatchStatus.FAILED;
            case COMPARISON_FAILED -> SearchShadowDispatchStatus.COMPARISON_FAILED;
        };
    }

    private static <T> SearchShadowDispatchReceiptV1<T> receipt(
            T legacyResponse, SearchShadowDispatchStatus status, SearchShadowSamplingDecisionV1 sampling,
            SearchShadowCircuitDecisionV1 circuit, SearchShadowIntegrationResult<T> integration,
            SearchShadowComparisonLogResultV1 log, String code) {
        return new SearchShadowDispatchReceiptV1<>(legacyResponse, status, sampling, circuit, integration, log, code,
                SearchShadowWiringAuthorityV1.legacyOnly());
    }
}
