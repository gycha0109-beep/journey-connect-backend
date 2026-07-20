package com.jc.backend.search.shadow.production;

import com.jc.backend.common.PageResponse;
import com.jc.backend.post.PostDtos;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreCompatibilityAdapter;
import com.jc.intelligence.integration.search.v1.SearchShadowIntegrationPort;
import com.jc.intelligence.integration.search.v1.SearchShadowIntegrationResult;
import com.jc.intelligence.integration.search.v1.SearchShadowStatus;
import com.jc.intelligence.production.search.v1.PrivacySafeSearchShadowEvidenceV1;
import com.jc.intelligence.production.search.v1.ProductionShadowDispatchStatus;
import com.jc.intelligence.production.search.v1.ProductionShadowTaskCompletionStatus;
import com.jc.intelligence.production.search.v1.ProjectionExploreRuntimeInputProviderFactory;
import com.jc.intelligence.production.search.v1.SearchProductionContractIds;
import com.jc.intelligence.production.search.v1.SearchShadowEvidenceSink;
import com.jc.intelligence.production.search.v1.SearchShadowEvidenceStatus;
import com.jc.intelligence.production.search.v1.SearchShadowMetricName;
import com.jc.intelligence.production.search.v1.SearchShadowMetricSink;
import com.jc.intelligence.wiring.search.v1.SearchShadowCircuitDecisionV1;
import com.jc.intelligence.wiring.search.v1.SearchShadowCircuitState;
import com.jc.intelligence.wiring.search.v1.SearchShadowDispatchReceiptV1;
import com.jc.intelligence.wiring.search.v1.SearchShadowDispatchStatus;
import com.jc.intelligence.integration.search.v1.SearchShadowFingerprintV1;
import com.jc.intelligence.wiring.search.v1.SearchShadowHook;
import com.jc.intelligence.wiring.search.v1.SearchShadowHookRequestV1;
import com.jc.intelligence.wiring.search.v1.SearchShadowWiringAuthorityV1;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/** Production-profile hook. It only gates and submits; the legacy request thread never joins shadow work. */
public final class ProductionExploreSearchShadowHook
        implements SearchShadowHook<PageResponse<PostDtos.Summary>> {
    private static final SearchShadowCircuitDecisionV1 CLOSED_CIRCUIT =
            new SearchShadowCircuitDecisionV1(SearchShadowCircuitState.CLOSED, true, "closed");

    private final ProductionInternalAccountHashResolver accountHashResolver;
    private final ProductionSearchShadowOperationalGate gate;
    private final SearchShadowIntegrationPort<PageResponse<PostDtos.Summary>> integrationPort;
    private final ProjectionExploreRuntimeInputProviderFactory providerFactory;
    private final SearchShadowMetricSink metrics;
    private final SearchShadowEvidenceSink evidenceSink;
    private final ProductionSearchShadowOperationalLogger logger;
    private final Clock clock;
    private final LegacyExploreCompatibilityAdapter compatibilityAdapter = new LegacyExploreCompatibilityAdapter();

    public ProductionExploreSearchShadowHook(
            ProductionInternalAccountHashResolver accountHashResolver,
            ProductionSearchShadowOperationalGate gate,
            SearchShadowIntegrationPort<PageResponse<PostDtos.Summary>> integrationPort,
            ProjectionExploreRuntimeInputProviderFactory providerFactory,
            SearchShadowMetricSink metrics,
            SearchShadowEvidenceSink evidenceSink,
            ProductionSearchShadowOperationalLogger logger,
            Clock clock) {
        this.accountHashResolver = Objects.requireNonNull(accountHashResolver, "accountHashResolver");
        this.gate = Objects.requireNonNull(gate, "gate");
        this.integrationPort = Objects.requireNonNull(integrationPort, "integrationPort");
        this.providerFactory = Objects.requireNonNull(providerFactory, "providerFactory");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.evidenceSink = Objects.requireNonNull(evidenceSink, "evidenceSink");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public SearchShadowDispatchReceiptV1<PageResponse<PostDtos.Summary>> dispatch(
            SearchShadowHookRequestV1<PageResponse<PostDtos.Summary>> request) {
        Objects.requireNonNull(request, "request");
        var decision = gate.dispatch(
                accountHashResolver::currentAccountHash,
                () -> execute(request),
                completion -> {
                    logger.completion(completion.safeReason());
                    safeDuration(SearchShadowMetricName.TOTAL_LATENCY, completion.elapsed(),
                            Map.of("outcome", completion.status().name().toLowerCase(java.util.Locale.ROOT)));
                    if (completion.status() == ProductionShadowTaskCompletionStatus.TIMED_OUT
                            || completion.status() == ProductionShadowTaskCompletionStatus.HARD_TIMEOUT) {
                        safeIncrement(SearchShadowMetricName.TIMEOUT, Map.of("reason", "timeout"));
                    } else if (completion.status() == ProductionShadowTaskCompletionStatus.FAILED) {
                        safeIncrement(SearchShadowMetricName.RUNTIME_FAILURE, Map.of("reason", "task_failure"));
                    }
                });
        logger.decision(decision.reason());
        return receipt(request.legacyResponse(), decision);
    }

    private void execute(SearchShadowHookRequestV1<PageResponse<PostDtos.Summary>> request) {
        long started = System.nanoTime();
        try {
            var compatibility = compatibilityAdapter.adapt(
                    request.legacyRequest(), request.legacyPage(), request.compatibilityContext());
            var provider = providerFactory.create(request.legacyRequest(), request.shadowContext());
            SearchShadowIntegrationResult<PageResponse<PostDtos.Summary>> result = integrationPort.integrate(
                    request.legacyResponse(), compatibility, request.shadowContext(), provider);
            if (result.legacyResponse() != request.legacyResponse()) {
                throw new IllegalStateException("legacy response identity violation");
            }
            recordResult(request, result, Duration.ofNanos(Math.max(0L, System.nanoTime() - started)));
        } catch (RuntimeException ignored) {
            safeIncrement(SearchShadowMetricName.RUNTIME_FAILURE, Map.of("reason", "shadow_failure"));
            logger.completion("shadow_failure");
        }
    }

    private void recordResult(
            SearchShadowHookRequestV1<PageResponse<PostDtos.Summary>> request,
            SearchShadowIntegrationResult<PageResponse<PostDtos.Summary>> result,
            Duration totalDuration) {
        SearchShadowStatus status = result.shadowStatus();
        safeIncrement(metricFor(status), Map.of("outcome", safeOutcome(status)));
        safeDuration(SearchShadowMetricName.TOTAL_LATENCY, totalDuration, Map.of("outcome", safeOutcome(status)));
        var comparison = result.comparisonEvidence();
        if (comparison == null) {
            return;
        }
        safeDuration(SearchShadowMetricName.RUNTIME_LATENCY, comparison.metrics().runtimeDuration(),
                Map.of("outcome", safeOutcome(status)));
        safeIncrement(SearchShadowMetricName.OVERLAP_BUCKET,
                Map.of("bucket", overlapBucket(comparison.metrics().topKOverlapRatio())));
        safeIncrement(SearchShadowMetricName.DIVERGENCE_BUCKET,
                Map.of("bucket", comparison.mismatches().isEmpty() ? "none" : "present"));
        try {
            evidenceSink.record(new PrivacySafeSearchShadowEvidenceV1(
                    clock.instant(),
                    "run:" + SearchShadowFingerprintV1.sha256(request.shadowContext().correlationId()).substring(0, 32),
                    SearchProductionContractIds.PROJECTION_SCHEMA.value(),
                    SearchProductionContractIds.ELIGIBILITY_POLICY.value(),
                    evidenceStatus(status),
                    latencyBucket(totalDuration),
                    countBucket(comparison.metrics().runtimeCount()),
                    overlapBucket(comparison.metrics().topKOverlapRatio()),
                    comparison.mismatches().isEmpty() ? "none" : "present",
                    comparison.runtimeInputStatus().wireValue(),
                    safeOutcome(status)));
        } catch (RuntimeException ignored) {
            safeIncrement(SearchShadowMetricName.EVIDENCE_FAILURE, Map.of("reason", "evidence_failure"));
        }
    }

    private void safeIncrement(SearchShadowMetricName name, Map<String, String> tags) {
        try { metrics.increment(name, tags); } catch (RuntimeException ignored) { }
    }

    private void safeDuration(SearchShadowMetricName name, Duration duration, Map<String, String> tags) {
        try { metrics.recordDuration(name, duration, tags); } catch (RuntimeException ignored) { }
    }

    private static SearchShadowMetricName metricFor(SearchShadowStatus status) {
        return switch (status) {
            case COMPARED, NOT_COMPARABLE -> SearchShadowMetricName.COMPLETED;
            case INPUT_UNAVAILABLE, INPUT_UNSUPPORTED, INVALID_INPUT -> SearchShadowMetricName.INPUT_UNAVAILABLE;
            case TIMED_OUT -> SearchShadowMetricName.TIMEOUT;
            case COMPARISON_FAILED -> SearchShadowMetricName.COMPARISON_FAILURE;
            case RUNTIME_FAILED -> SearchShadowMetricName.RUNTIME_FAILURE;
            case DISABLED -> SearchShadowMetricName.SKIPPED;
        };
    }

    private static SearchShadowEvidenceStatus evidenceStatus(SearchShadowStatus status) {
        return switch (status) {
            case COMPARED, NOT_COMPARABLE -> SearchShadowEvidenceStatus.COMPLETED;
            case INPUT_UNAVAILABLE, INPUT_UNSUPPORTED, INVALID_INPUT -> SearchShadowEvidenceStatus.INPUT_UNAVAILABLE;
            case TIMED_OUT -> SearchShadowEvidenceStatus.TIMED_OUT;
            case COMPARISON_FAILED -> SearchShadowEvidenceStatus.COMPARISON_FAILED;
            case RUNTIME_FAILED -> SearchShadowEvidenceStatus.RUNTIME_FAILED;
            case DISABLED -> SearchShadowEvidenceStatus.SKIPPED;
        };
    }

    private static String safeOutcome(SearchShadowStatus status) {
        return status.wireValue();
    }

    private static String latencyBucket(Duration duration) {
        long millis = duration.toMillis();
        if (millis < 50) return "lt_50ms";
        if (millis < 100) return "lt_100ms";
        if (millis < 200) return "lt_200ms";
        return "gte_200ms";
    }

    private static String countBucket(int count) {
        if (count == 0) return "zero";
        if (count <= 10) return "one_to_ten";
        if (count <= 50) return "eleven_to_fifty";
        return "over_fifty";
    }

    private static String overlapBucket(double ratio) {
        if (ratio >= 0.9d) return "gte_90";
        if (ratio >= 0.5d) return "gte_50";
        if (ratio > 0.0d) return "lt_50";
        return "zero";
    }

    private static SearchShadowDispatchReceiptV1<PageResponse<PostDtos.Summary>> receipt(
            PageResponse<PostDtos.Summary> legacy,
            ProductionSearchShadowActivationDecision decision) {
        if (decision.dispatched()) {
            return new SearchShadowDispatchReceiptV1<>(legacy, SearchShadowDispatchStatus.SUBMITTED,
                    decision.samplingDecision(), CLOSED_CIRCUIT, null, null,
                    decision.reason().safeCode(), SearchShadowWiringAuthorityV1.legacyOnly());
        }
        if (decision.reason() == ProductionSearchShadowActivationReason.NOT_SAMPLED) {
            return new SearchShadowDispatchReceiptV1<>(legacy, SearchShadowDispatchStatus.NOT_SAMPLED,
                    decision.samplingDecision(), null, null, null,
                    decision.reason().safeCode(), SearchShadowWiringAuthorityV1.legacyOnly());
        }
        if (decision.reason() == ProductionSearchShadowActivationReason.RESOURCE_REJECTED) {
            SearchShadowDispatchStatus status = switch (decision.dispatchStatus()) {
                case QUEUE_FULL -> SearchShadowDispatchStatus.QUEUE_FULL;
                case EXECUTOR_UNAVAILABLE -> SearchShadowDispatchStatus.EXECUTOR_UNAVAILABLE;
                default -> SearchShadowDispatchStatus.REJECTED;
            };
            return new SearchShadowDispatchReceiptV1<>(legacy, status,
                    decision.samplingDecision(), CLOSED_CIRCUIT, null, null,
                    decision.reason().safeCode(), SearchShadowWiringAuthorityV1.legacyOnly());
        }
        return new SearchShadowDispatchReceiptV1<>(legacy, SearchShadowDispatchStatus.DISABLED,
                null, null, null, null,
                decision.reason().safeCode(), SearchShadowWiringAuthorityV1.legacyOnly());
    }
}
