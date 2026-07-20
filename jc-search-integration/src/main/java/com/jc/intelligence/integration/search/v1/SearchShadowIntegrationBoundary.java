package com.jc.intelligence.integration.search.v1;

import com.jc.intelligence.compat.search.explore.v1.LegacyExploreCompatibilityResult;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreContractIds;
import com.jc.intelligence.runtime.search.v1.SearchRuntime;
import com.jc.intelligence.runtime.search.v1.SearchRuntimeResultV1;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SearchShadowIntegrationBoundary<T> implements SearchShadowIntegrationPort<T> {
    private static final List<SearchShadowWarningCode> BASE_WARNINGS = List.of(
            SearchShadowWarningCode.LEGACY_OFFSET_PRESERVED,
            SearchShadowWarningCode.LEGACY_LATEST_ORDER_NOT_RANKING,
            SearchShadowWarningCode.RAW_QUERY_OMITTED,
            SearchShadowWarningCode.NON_PERSISTENT_EVIDENCE,
            SearchShadowWarningCode.NON_AUTHORITATIVE_METRIC,
            SearchShadowWarningCode.PRODUCTION_WIRING_DISABLED);

    private final SearchShadowPolicyV1 policy;
    private final SearchRuntime runtime;
    private final SearchShadowExecutionPort executionPort;
    private final SearchShadowComparator comparisonHarness;
    private final SearchShadowResponseGuard responseGuard;

    public SearchShadowIntegrationBoundary(
            SearchShadowPolicyV1 policy,
            SearchRuntime runtime,
            SearchShadowExecutionPort executionPort) {
        this(policy, runtime, executionPort, new SearchShadowComparisonHarness(), new SearchShadowResponseGuard());
    }

    public SearchShadowIntegrationBoundary(
            SearchShadowPolicyV1 policy,
            SearchRuntime runtime,
            SearchShadowExecutionPort executionPort,
            SearchShadowComparator comparisonHarness,
            SearchShadowResponseGuard responseGuard) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.executionPort = Objects.requireNonNull(executionPort, "executionPort");
        this.comparisonHarness = Objects.requireNonNull(comparisonHarness, "comparisonHarness");
        this.responseGuard = Objects.requireNonNull(responseGuard, "responseGuard");
    }

    @Override public SearchShadowIntegrationResult<T> integrate(
            T legacyResponse,
            LegacyExploreCompatibilityResult legacyCompatibility,
            SearchShadowContextV1 context,
            SearchShadowRuntimeInputProvider runtimeInputProvider) {
        Objects.requireNonNull(legacyResponse, "legacyResponse");
        Objects.requireNonNull(legacyCompatibility, "legacyCompatibility");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(runtimeInputProvider, "runtimeInputProvider");
        SearchShadowActivationDecisionV1 activation = SearchShadowActivationDecisionV1.decide(policy);
        if (!activation.activated()) {
            return new SearchShadowIntegrationResult<>(responseGuard.preserve(legacyResponse, legacyResponse),
                    SearchShadowStatus.DISABLED, null, SearchShadowAuthorityV1.legacyOnly());
        }
        String requestFingerprint = legacyCompatibility.evidence() == null
                ? SearchShadowFingerprintV1.sha256("legacy-request-unavailable")
                : legacyCompatibility.evidence().legacyRequestFingerprint();
        String responseFingerprint = legacyCompatibility.evidence() == null
                ? SearchShadowFingerprintV1.sha256("legacy-response-unavailable")
                : legacyCompatibility.evidence().legacyResponseFingerprint();
        SearchShadowRuntimeInputContextV1 inputContext = new SearchShadowRuntimeInputContextV1(
                context.correlationId(), context.referenceTime(), requestFingerprint, responseFingerprint);
        final SearchShadowRuntimeInputResultV1 input;
        try {
            input = Objects.requireNonNull(runtimeInputProvider.provide(inputContext), "runtime input provider returned null");
        } catch (RuntimeException exception) {
            return finish(legacyResponse, legacyCompatibility, context, SearchShadowStatus.INVALID_INPUT,
                    SearchShadowRuntimeInputStatus.INVALID, null, null, "runtime_input_invalid",
                    comparisonFor(SearchShadowMismatchCode.RUNTIME_INPUT_INVALID, SearchShadowSeverity.ERROR, legacyCompatibility.items().size()),
                    Duration.ZERO);
        }
        if (input.status() != SearchShadowRuntimeInputStatus.AVAILABLE) {
            SearchShadowMismatchCode code = input.status() == SearchShadowRuntimeInputStatus.UNSUPPORTED
                    ? SearchShadowMismatchCode.RUNTIME_INPUT_UNSUPPORTED
                    : SearchShadowMismatchCode.RUNTIME_INPUT_UNAVAILABLE;
            SearchShadowStatus status = input.status() == SearchShadowRuntimeInputStatus.UNSUPPORTED
                    ? SearchShadowStatus.INPUT_UNSUPPORTED
                    : input.status() == SearchShadowRuntimeInputStatus.INVALID
                            ? SearchShadowStatus.INVALID_INPUT : SearchShadowStatus.INPUT_UNAVAILABLE;
            return finish(legacyResponse, legacyCompatibility, context, status, input.status(), null, null,
                    input.safeReason(), comparisonFor(code, SearchShadowSeverity.NOT_COMPARABLE, legacyCompatibility.items().size()), Duration.ZERO);
        }

        final SearchShadowExecutionOutcomeV1 outcome;
        try {
            outcome = Objects.requireNonNull(executionPort.execute(runtime, input.executionRequest(),
                    new SearchShadowExecutionDeadlineV1(context.referenceTime(), policy.timeout())),
                    "execution port returned null");
        } catch (RuntimeException exception) {
            return finish(legacyResponse, legacyCompatibility, context, SearchShadowStatus.RUNTIME_FAILED,
                    input.status(), input.runtimeInputFingerprint(), null, "runtime_execution_failed",
                    comparisonFor(SearchShadowMismatchCode.RUNTIME_FAILURE, SearchShadowSeverity.ERROR, legacyCompatibility.items().size()), Duration.ZERO);
        }
        if (outcome.status() == SearchShadowExecutionStatus.TIMED_OUT) {
            return finish(legacyResponse, legacyCompatibility, context, SearchShadowStatus.TIMED_OUT,
                    input.status(), input.runtimeInputFingerprint(), null, "timed_out",
                    comparisonFor(SearchShadowMismatchCode.RUNTIME_TIMEOUT, SearchShadowSeverity.WARNING, legacyCompatibility.items().size()),
                    outcome.runtimeDuration());
        }
        if (outcome.status() == SearchShadowExecutionStatus.FAILED) {
            return finish(legacyResponse, legacyCompatibility, context, SearchShadowStatus.RUNTIME_FAILED,
                    input.status(), input.runtimeInputFingerprint(), null, outcome.safeFailureCode(),
                    comparisonFor(SearchShadowMismatchCode.RUNTIME_FAILURE, SearchShadowSeverity.ERROR, legacyCompatibility.items().size()),
                    outcome.runtimeDuration());
        }

        SearchRuntimeResultV1 runtimeResult = outcome.runtimeResult();
        String runtimeResultFingerprint = SearchShadowFingerprintV1.runtimeResult(runtimeResult);
        final SearchShadowComparisonResultV1 comparison;
        try {
            comparison = comparisonHarness.compare(legacyCompatibility, runtimeResult, policy.topK(), outcome.runtimeDuration());
        } catch (RuntimeException exception) {
            return finish(legacyResponse, legacyCompatibility, context, SearchShadowStatus.COMPARISON_FAILED,
                    input.status(), input.runtimeInputFingerprint(), runtimeResultFingerprint, "comparison_failed",
                    comparisonFailure(outcome.runtimeDuration()), outcome.runtimeDuration());
        }
        SearchShadowStatus status = comparison.status() == SearchShadowComparisonStatus.COMPARED
                ? SearchShadowStatus.COMPARED : comparison.status() == SearchShadowComparisonStatus.NOT_COMPARABLE
                        ? SearchShadowStatus.NOT_COMPARABLE : SearchShadowStatus.COMPARISON_FAILED;
        return finish(legacyResponse, legacyCompatibility, context, status, input.status(),
                input.runtimeInputFingerprint(), runtimeResultFingerprint, runtimeResult.status().wireValue(),
                comparison, outcome.runtimeDuration());
    }

    private SearchShadowIntegrationResult<T> finish(
            T legacyResponse,
            LegacyExploreCompatibilityResult legacyCompatibility,
            SearchShadowContextV1 context,
            SearchShadowStatus integrationStatus,
            SearchShadowRuntimeInputStatus inputStatus,
            String runtimeInputFingerprint,
            String runtimeResultFingerprint,
            String runtimeStatus,
            SearchShadowComparisonResultV1 comparison,
            Duration runtimeDuration) {
        SearchShadowComparisonMetricsV1 metrics = comparison.metrics();
        if (!metrics.runtimeDuration().equals(runtimeDuration)) {
            metrics = new SearchShadowComparisonMetricsV1(metrics.legacyCount(), metrics.runtimeCount(),
                    metrics.intersectionCount(), metrics.legacyOnlyCount(), metrics.runtimeOnlyCount(),
                    metrics.topKOverlapCount(), metrics.topKOverlapRatio(), metrics.sameOrderPrefixLength(),
                    metrics.duplicateCount(), metrics.comparisonDuration(), runtimeDuration);
            comparison = new SearchShadowComparisonResultV1(comparison.status(), metrics, comparison.mismatches());
        }
        String comparisonHash = SearchShadowFingerprintV1.comparison(legacyCompatibility, runtimeInputFingerprint,
                runtimeResultFingerprint, policy, comparison);
        String comparisonIdHash = SearchShadowFingerprintV1.sha256(comparisonHash + "\n" + context.correlationId() + "\n" + context.referenceTime());
        String comparisonId = "comparison:" + comparisonIdHash.substring(0, 32);
        SearchShadowComparisonEvidenceV1 evidence = new SearchShadowComparisonEvidenceV1(
                SearchIntegrationContractIds.COMPARISON_EVIDENCE,
                comparisonId,
                context.correlationId(),
                policy.mode(),
                policy.shadowPolicyVersion(),
                LegacyExploreContractIds.ENDPOINT_ID,
                legacyCompatibility.evidence() == null ? SearchShadowFingerprintV1.sha256("legacy-request-unavailable")
                        : legacyCompatibility.evidence().legacyRequestFingerprint(),
                legacyCompatibility.evidence() == null ? SearchShadowFingerprintV1.sha256("legacy-response-unavailable")
                        : legacyCompatibility.evidence().legacyResponseFingerprint(),
                runtimeInputFingerprint,
                runtimeResultFingerprint,
                policy.comparisonPolicyVersion(),
                context.referenceTime(),
                integrationStatus,
                inputStatus,
                normalizeRuntimeStatus(runtimeStatus),
                comparison.status(),
                comparison.metrics(),
                comparison.mismatches(),
                BASE_WARNINGS,
                comparison.maximumSeverity(),
                policy.producerBuildId(),
                SearchShadowAuthorityV1.legacyOnly());
        return new SearchShadowIntegrationResult<>(responseGuard.preserve(legacyResponse, legacyResponse),
                integrationStatus, evidence, SearchShadowAuthorityV1.legacyOnly());
    }

    private static SearchShadowComparisonResultV1 comparisonFailure(Duration runtimeDuration) {
        SearchShadowMismatchV1 mismatch = new SearchShadowMismatchV1(
                SearchShadowMismatchCode.COMPARISON_FAILURE, SearchShadowSeverity.ERROR, null, null, null);
        return new SearchShadowComparisonResultV1(SearchShadowComparisonStatus.FAILED,
                new SearchShadowComparisonMetricsV1(0, 0, 0, 0, 0, 0, 1.0d, 0, 0,
                        Duration.ZERO, runtimeDuration), List.of(mismatch));
    }

    private static String normalizeRuntimeStatus(String value) {
        if (value == null || value.isBlank()) return "unavailable";
        String normalized = value.toLowerCase(java.util.Locale.ROOT).replace('-', '_');
        return normalized.matches("[a-z][a-z0-9_]{0,63}") ? normalized : "unavailable";
    }

    private static SearchShadowComparisonResultV1 comparisonFor(
            SearchShadowMismatchCode code, SearchShadowSeverity severity, int legacyCount) {
        SearchShadowMismatchV1 mismatch = new SearchShadowMismatchV1(code, severity, null, null, null);
        SearchShadowComparisonStatus status = code == SearchShadowMismatchCode.COMPARISON_FAILURE
                ? SearchShadowComparisonStatus.FAILED : SearchShadowComparisonStatus.NOT_COMPARABLE;
        return new SearchShadowComparisonResultV1(status,
                new SearchShadowComparisonMetricsV1(legacyCount, 0, 0, legacyCount, 0, 0,
                        legacyCount == 0 ? 1.0d : 0.0d, 0, 0,
                        Duration.ZERO, Duration.ZERO), List.of(mismatch));
    }
}
