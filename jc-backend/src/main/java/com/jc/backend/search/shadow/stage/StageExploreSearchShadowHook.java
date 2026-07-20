package com.jc.backend.search.shadow.stage;

import com.jc.backend.common.PageResponse;
import com.jc.backend.post.PostDtos;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreCompatibilityAdapter;
import com.jc.intelligence.integration.search.v1.SearchShadowFingerprintV1;
import com.jc.intelligence.integration.search.v1.SearchShadowIntegrationPort;
import com.jc.intelligence.integration.search.v1.SearchShadowRuntimeInputProvider;
import com.jc.intelligence.wiring.search.v1.BackendExploreShadowHookAdapter;
import com.jc.intelligence.wiring.search.v1.DefaultSearchShadowDispatcher;
import com.jc.intelligence.wiring.search.v1.DeterministicSearchShadowSampler;
import com.jc.intelligence.wiring.search.v1.SearchShadowActivationDecisionV1;
import com.jc.intelligence.wiring.search.v1.SearchShadowActivationGate;
import com.jc.intelligence.wiring.search.v1.SearchShadowActivationInputsV1;
import com.jc.intelligence.wiring.search.v1.SearchShadowCircuitBreaker;
import com.jc.intelligence.wiring.search.v1.SearchShadowCircuitDecisionV1;
import com.jc.intelligence.wiring.search.v1.SearchShadowComparisonLogPort;
import com.jc.intelligence.wiring.search.v1.SearchShadowDispatchReceiptV1;
import com.jc.intelligence.wiring.search.v1.SearchShadowDispatchStatus;
import com.jc.intelligence.wiring.search.v1.SearchShadowHook;
import com.jc.intelligence.wiring.search.v1.SearchShadowHookRequestV1;
import com.jc.intelligence.wiring.search.v1.SearchShadowSampler;
import com.jc.intelligence.wiring.search.v1.SearchShadowSamplingDecisionV1;
import com.jc.intelligence.wiring.search.v1.SearchShadowSamplingPolicyV1;
import com.jc.intelligence.wiring.search.v1.SearchShadowWiringAuthorityV1;
import com.jc.intelligence.wiring.search.v1.SearchShadowWiringConfigV1;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/** Non-blocking test/stage hook. The request thread performs gating and bounded submission only. */
public final class StageExploreSearchShadowHook implements SearchShadowHook<PageResponse<PostDtos.Summary>> {
    private final SearchShadowWiringConfigV1 config;
    private final StageSearchShadowTaskExecutor taskExecutor;
    private final SearchShadowCircuitBreaker circuitBreaker;
    private final SearchShadowIntegrationPort<PageResponse<PostDtos.Summary>> integrationPort;
    private final StageExploreSearchRuntimeInputProviderFactory providerFactory;
    private final SearchShadowComparisonLogPort logPort;
    private final LegacyExploreCompatibilityAdapter compatibilityAdapter;
    private final SearchShadowActivationGate activationGate = new SearchShadowActivationGate();
    private final SearchShadowSampler sampler = new DeterministicSearchShadowSampler();
    private final AtomicLong completedDispatches = new AtomicLong();

    public StageExploreSearchShadowHook(
            SearchShadowWiringConfigV1 config,
            StageSearchShadowTaskExecutor taskExecutor,
            SearchShadowCircuitBreaker circuitBreaker,
            SearchShadowIntegrationPort<PageResponse<PostDtos.Summary>> integrationPort,
            StageExploreSearchRuntimeInputProviderFactory providerFactory,
            SearchShadowComparisonLogPort logPort) {
        this.config = Objects.requireNonNull(config, "config");
        this.taskExecutor = Objects.requireNonNull(taskExecutor, "taskExecutor");
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker, "circuitBreaker");
        this.integrationPort = Objects.requireNonNull(integrationPort, "integrationPort");
        this.providerFactory = Objects.requireNonNull(providerFactory, "providerFactory");
        this.logPort = Objects.requireNonNull(logPort, "logPort");
        this.compatibilityAdapter = new LegacyExploreCompatibilityAdapter();
    }

    @Override public SearchShadowDispatchReceiptV1<PageResponse<PostDtos.Summary>> dispatch(
            SearchShadowHookRequestV1<PageResponse<PostDtos.Summary>> request) {
        Objects.requireNonNull(request, "request");
        SearchShadowActivationDecisionV1 activation = activationGate.decide(config,
                new SearchShadowActivationInputsV1(true,
                        taskExecutor.available() && taskExecutor.queueCapacity() >= config.queueCapacity()
                                && taskExecutor.maxConcurrency() >= config.maxConcurrency(),
                        logPort.available()));
        if (!activation.activated()) {
            SearchShadowDispatchStatus status = "profile_blocked".equals(activation.reasonCode())
                    ? SearchShadowDispatchStatus.PROFILE_BLOCKED : SearchShadowDispatchStatus.DISABLED;
            return receipt(request.legacyResponse(), status, null, null, activation.reasonCode());
        }
        SearchShadowSamplingDecisionV1 sampling = sampler.decide(request.shadowContext().correlationId(),
                new SearchShadowSamplingPolicyV1(config.sampleBasisPoints(), config.samplingPolicyVersion()));
        if (!sampling.included()) {
            return receipt(request.legacyResponse(), SearchShadowDispatchStatus.NOT_SAMPLED, sampling, null, "not_sampled");
        }
        SearchShadowCircuitDecisionV1 circuit = circuitBreaker.evaluate(
                SearchShadowFingerprintV1.sha256(request.shadowContext().correlationId()));
        if (!circuit.permitted()) {
            return receipt(request.legacyResponse(), SearchShadowDispatchStatus.CIRCUIT_OPEN, sampling, circuit,
                    circuit.reasonCode());
        }
        StageSearchShadowSubmissionResult submission = taskExecutor.submit(() -> execute(request));
        return switch (submission.status()) {
            case ACCEPTED -> receipt(request.legacyResponse(), SearchShadowDispatchStatus.SUBMITTED, sampling, circuit,
                    submission.safeCode());
            case QUEUE_FULL -> receipt(request.legacyResponse(), SearchShadowDispatchStatus.QUEUE_FULL, sampling, circuit,
                    submission.safeCode());
            case EXECUTOR_UNAVAILABLE -> receipt(request.legacyResponse(), SearchShadowDispatchStatus.EXECUTOR_UNAVAILABLE,
                    sampling, circuit, submission.safeCode());
            case REJECTED -> receipt(request.legacyResponse(), SearchShadowDispatchStatus.REJECTED, sampling, circuit,
                    submission.safeCode());
        };
    }

    private void execute(SearchShadowHookRequestV1<PageResponse<PostDtos.Summary>> request) {
        SearchShadowRuntimeInputProvider provider = providerFactory.create(request.legacyRequest(), request.shadowContext());
        var dispatcher = new DefaultSearchShadowDispatcher<>(config,
                new DirectStageSearchShadowExecutor(config.queueCapacity(), config.maxConcurrency()), circuitBreaker,
                integrationPort, provider, logPort);
        new BackendExploreShadowHookAdapter<PageResponse<PostDtos.Summary>>(compatibilityAdapter, dispatcher)
                .dispatch(request);
        completedDispatches.incrementAndGet();
    }

    public long completedDispatchCount() { return completedDispatches.get(); }

    private static SearchShadowDispatchReceiptV1<PageResponse<PostDtos.Summary>> receipt(
            PageResponse<PostDtos.Summary> response,
            SearchShadowDispatchStatus status,
            SearchShadowSamplingDecisionV1 sampling,
            SearchShadowCircuitDecisionV1 circuit,
            String code) {
        return new SearchShadowDispatchReceiptV1<>(response, status, sampling, circuit, null, null, code,
                SearchShadowWiringAuthorityV1.legacyOnly());
    }
}
