package com.jc.backend.search.shadow.stage;

import static org.assertj.core.api.Assertions.assertThat;

import com.jc.backend.common.PageResponse;
import com.jc.backend.post.PostDtos;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreCompatibilityContext;
import com.jc.intelligence.compat.search.explore.v1.LegacyExplorePageView;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreRequestView;
import com.jc.intelligence.contract.v1.version.PolicyVersion;
import com.jc.intelligence.contract.v1.version.ProducerBuildId;
import com.jc.intelligence.integration.search.v1.SearchShadowContextV1;
import com.jc.intelligence.integration.search.v1.SearchShadowIntegrationResult;
import com.jc.intelligence.integration.search.v1.SearchShadowStatus;
import com.jc.intelligence.integration.search.v1.SearchShadowAuthorityV1;
import com.jc.intelligence.wiring.search.v1.FixedSearchShadowCircuitBreaker;
import com.jc.intelligence.wiring.search.v1.SearchShadowCircuitState;
import com.jc.intelligence.wiring.search.v1.SearchShadowDispatchStatus;
import com.jc.intelligence.wiring.search.v1.SearchShadowHookRequestV1;
import com.jc.intelligence.wiring.search.v1.SearchShadowWiringConfigV1;
import com.jc.intelligence.wiring.search.v1.SearchShadowWiringMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class StageExploreSearchShadowFailureIsolationTest {
    private static final Instant TIME = Instant.parse("2026-07-19T03:00:00Z");

    @Test
    void providerAndLoggingFailuresNeverChangeLegacyResponse() {
        PageResponse<PostDtos.Summary> response = response();
        StageSearchShadowTaskExecutor executor = new StageSearchShadowTaskExecutor(1, 2);
        try {
            var hook = new StageExploreSearchShadowHook(config(10_000), executor,
                    new FixedSearchShadowCircuitBreaker(SearchShadowCircuitState.CLOSED, false),
                    (legacy, compatibility, context, provider) -> {
                        provider.provide(new com.jc.intelligence.integration.search.v1.SearchShadowRuntimeInputContextV1(
                                context.correlationId(), context.referenceTime(),
                                com.jc.intelligence.integration.search.v1.SearchShadowFingerprintV1.sha256("request"),
                                com.jc.intelligence.integration.search.v1.SearchShadowFingerprintV1.sha256("response")));
                        return new SearchShadowIntegrationResult<>(legacy, SearchShadowStatus.NOT_COMPARABLE, null,
                                SearchShadowAuthorityV1.legacyOnly());
                    },
                    (request, context) -> ignored -> { throw new IllegalStateException("provider failed"); },
                    new InMemoryStageSearchShadowComparisonLogPort(10));
            var receipt = hook.dispatch(request(response));
            assertThat(receipt.status()).isEqualTo(SearchShadowDispatchStatus.SUBMITTED);
            assertThat(executor.awaitIdle(Duration.ofSeconds(1))).isTrue();
            assertThat(response).isSameAs(receipt.legacyResponse());
            assertThat(response.items()).hasSize(1);
        } finally {
            executor.close();
        }
    }

    @Test
    void sampleZeroCircuitOpenAndClosedExecutorSkipWithoutResponseImpact() {
        PageResponse<PostDtos.Summary> response = response();
        StageSearchShadowTaskExecutor zeroExecutor = new StageSearchShadowTaskExecutor(1, 1);
        try {
            var zero = new StageExploreSearchShadowHook(config(0), zeroExecutor,
                    new FixedSearchShadowCircuitBreaker(SearchShadowCircuitState.CLOSED, false),
                    (legacy, compatibility, context, provider) -> { throw new AssertionError("must not execute"); },
                    (request, context) -> { throw new AssertionError("must not create provider"); },
                    new InMemoryStageSearchShadowComparisonLogPort(10));
            assertThat(zero.dispatch(request(response)).status()).isEqualTo(SearchShadowDispatchStatus.NOT_SAMPLED);
            assertThat(zeroExecutor.acceptedCount()).isZero();
        } finally {
            zeroExecutor.close();
        }

        StageSearchShadowTaskExecutor circuitExecutor = new StageSearchShadowTaskExecutor(1, 1);
        try {
            var open = new StageExploreSearchShadowHook(config(10_000), circuitExecutor,
                    new FixedSearchShadowCircuitBreaker(SearchShadowCircuitState.OPEN, false),
                    (legacy, compatibility, context, provider) -> { throw new AssertionError("must not execute"); },
                    (request, context) -> { throw new AssertionError("must not create provider"); },
                    new InMemoryStageSearchShadowComparisonLogPort(10));
            assertThat(open.dispatch(request(response)).status()).isEqualTo(SearchShadowDispatchStatus.CIRCUIT_OPEN);
            assertThat(circuitExecutor.acceptedCount()).isZero();
        } finally {
            circuitExecutor.close();
        }

        StageSearchShadowTaskExecutor closed = new StageSearchShadowTaskExecutor(1, 1);
        closed.close();
        var unavailable = new StageExploreSearchShadowHook(config(10_000), closed,
                new FixedSearchShadowCircuitBreaker(SearchShadowCircuitState.CLOSED, false),
                (legacy, compatibility, context, provider) -> { throw new AssertionError("must not execute"); },
                (request, context) -> { throw new AssertionError("must not create provider"); },
                new InMemoryStageSearchShadowComparisonLogPort(10));
        assertThat(unavailable.dispatch(request(response)).status())
                .isEqualTo(SearchShadowDispatchStatus.DISABLED);
        assertThat(response.items()).hasSize(1);
    }

    private static SearchShadowWiringConfigV1 config(int sample) {
        return new SearchShadowWiringConfigV1(SearchShadowWiringMode.TEST_ONLY,
                SearchShadowWiringConfigV1.TEST_PROFILE, true, sample, Duration.ofMillis(100), 2, 1,
                new PolicyVersion("search-shadow-sampling-policy-v1"),
                new ProducerBuildId("ip10-test-stage-shadow"));
    }

    private static SearchShadowHookRequestV1<PageResponse<PostDtos.Summary>> request(
            PageResponse<PostDtos.Summary> response) {
        return new SearchShadowHookRequestV1<>(response,
                new LegacyExploreRequestView("서울", "서울", 0, 20, List.of(), java.util.Map.of()),
                new LegacyExplorePageView(List.of(), 0, 20, 1L, 1, true),
                new LegacyExploreCompatibilityContext("request:ip10", "correlation:ip10", "session:ip10", TIME,
                        TIME, new ProducerBuildId("ip10-test-stage-shadow")),
                new SearchShadowContextV1("request:ip10", "correlation:ip10", "session:ip10", TIME));
    }

    private static PageResponse<PostDtos.Summary> response() {
        return new PageResponse<>(List.of(new PostDtos.Summary(
                1L, "legacy", "KR-11", "서울", null, 0L, 0L, 0L,
                new PostDtos.Author(2L, "author", null), TIME)), 0, 20, 1L, 1, true);
    }
}
