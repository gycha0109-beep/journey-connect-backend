package com.jc.backend.search.shadow.production;

import static org.assertj.core.api.Assertions.assertThat;

import com.jc.backend.common.PageResponse;
import com.jc.backend.post.PostDtos;
import com.jc.backend.search.shadow.DefaultExploreShadowHookRequestFactory;
import com.jc.backend.search.shadow.ExploreShadowRequestContext;
import com.jc.intelligence.contract.v1.version.PolicyVersion;
import com.jc.intelligence.contract.v1.version.ProducerBuildId;
import com.jc.intelligence.integration.search.v1.SearchShadowIntegrationBoundary;
import com.jc.intelligence.integration.search.v1.SearchShadowMode;
import com.jc.intelligence.integration.search.v1.SearchShadowPolicyV1;
import com.jc.intelligence.production.search.v1.AllowlistedInternalCohortSelector;
import com.jc.intelligence.production.search.v1.InMemorySearchProjectionStore;
import com.jc.intelligence.production.search.v1.InMemorySearchShadowEvidenceSink;
import com.jc.intelligence.production.search.v1.InMemorySearchShadowMetricSink;
import com.jc.intelligence.production.search.v1.ProductionProjectionSearchRuntimeFactory;
import com.jc.intelligence.production.search.v1.ProductionShadowResourcePolicyV1;
import com.jc.intelligence.production.search.v1.ProductionShadowTaskExecutor;
import com.jc.intelligence.production.search.v1.ProjectionDeletionStatus;
import com.jc.intelligence.production.search.v1.ProjectionExploreRuntimeInputProviderFactory;
import com.jc.intelligence.production.search.v1.ProjectionModerationStatus;
import com.jc.intelligence.production.search.v1.ProjectionPublicationStatus;
import com.jc.intelligence.production.search.v1.ProjectionVisibilityStatus;
import com.jc.intelligence.production.search.v1.SearchDocumentEligibilityPolicyV1;
import com.jc.intelligence.production.search.v1.SearchDocumentProjectorV1;
import com.jc.intelligence.production.search.v1.SearchDocumentSourceV1;
import com.jc.intelligence.production.search.v1.SearchProjectionProjectorService;
import com.jc.intelligence.production.search.v1.SearchShadowMetricName;
import com.jc.intelligence.production.search.v1.TestMutableSearchShadowKillSwitch;
import com.jc.intelligence.wiring.search.v1.SearchShadowDispatchStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class ProductionSearchShadowDisableDrillTest {
    private static final Instant NOW = Instant.parse("2026-07-20T00:00:00Z");

    @Test
    void internalFixtureDispatchesThenRestartEquivalentKillStopsAllNewWork() throws Exception {
        String includedHash = findIncludedHash();
        var store = new InMemorySearchProjectionStore();
        var projector = new SearchProjectionProjectorService(
                new SearchDocumentEligibilityPolicyV1(Duration.ofDays(7)),
                new SearchDocumentProjectorV1(),
                store,
                Clock.fixed(NOW, ZoneOffset.UTC));
        projector.apply(new SearchDocumentSourceV1(
                1L, 1L, 11L, "kr-seoul", "place:101", "Seoul cafe", "public journey content",
                ProjectionVisibilityStatus.PUBLIC, ProjectionPublicationStatus.PUBLISHED,
                ProjectionModerationStatus.ELIGIBLE, ProjectionDeletionStatus.ACTIVE, false,
                NOW.minusSeconds(30)));

        var config = new ProductionSearchShadowRuntimeConfig(
                true, false, 10, 10, Set.of(includedHash),
                "approval:ip12-5-drill", "role:project-owner", "role:release-operator",
                "role:backend-owner", "metric:journey.search.shadow",
                NOW.minusSeconds(60), NOW.plusSeconds(3600),
                100, 1, 2, 8, Duration.ofMillis(200), Duration.ofMillis(300));
        var killSwitch = new TestMutableSearchShadowKillSwitch();
        killSwitch.enable();
        var metrics = new InMemorySearchShadowMetricSink();
        var evidence = new InMemorySearchShadowEvidenceSink(20);
        try (var executor = new ProductionShadowTaskExecutor(ProductionShadowResourcePolicyV1.approvedInitialPilot())) {
            var gate = new ProductionSearchShadowOperationalGate(
                    config,
                    killSwitch,
                    new AllowlistedInternalCohortSelector(Set.of(includedHash), true),
                    new ProductionSearchShadowSamplingGate(10),
                    executor,
                    metrics,
                    Clock.fixed(NOW, ZoneOffset.UTC));
            var runtime = ProductionProjectionSearchRuntimeFactory.create(store, Duration.ofHours(24));
            var integration = new SearchShadowIntegrationBoundary<PageResponse<PostDtos.Summary>>(
                    new SearchShadowPolicyV1(
                            SearchShadowMode.SHADOW_ENABLED,
                            new PolicyVersion("search-shadow-production-policy-v1"),
                            new PolicyVersion("search-shadow-comparison-policy-v1"),
                            Duration.ofMillis(200),
                            10,
                            new ProducerBuildId("ip12-disable-drill")),
                    runtime,
                    new DirectProductionSearchShadowExecutionPort());
            var hook = new ProductionExploreSearchShadowHook(
                    () -> Optional.of(includedHash),
                    gate,
                    integration,
                    new ProjectionExploreRuntimeInputProviderFactory(100),
                    metrics,
                    evidence,
                    new ProductionSearchShadowOperationalLogger(),
                    Clock.fixed(NOW, ZoneOffset.UTC));
            var request = request();
            var first = hook.dispatch(request);
            assertThat(first.status()).isEqualTo(SearchShadowDispatchStatus.SUBMITTED);
            assertThat(first.legacyResponse()).isSameAs(request.legacyResponse());
            awaitMetric(metrics, SearchShadowMetricName.COMPLETED);
            long completed = metrics.value(SearchShadowMetricName.COMPLETED);

            killSwitch.kill();
            var killedRequest = request();
            var killed = hook.dispatch(killedRequest);
            assertThat(killed.status()).isEqualTo(SearchShadowDispatchStatus.DISABLED);
            assertThat(killed.legacyResponse()).isSameAs(killedRequest.legacyResponse());
            Thread.sleep(30);
            assertThat(metrics.value(SearchShadowMetricName.COMPLETED)).isEqualTo(completed);
            assertThat(metrics.value(SearchShadowMetricName.KILLED)).isGreaterThanOrEqualTo(1L);
        }
    }

    @Test
    void emptyCohortNeverDispatchesEvenWhenEnabledAtTenBps() throws Exception {
        var config = new ProductionSearchShadowRuntimeConfig(
                true, false, 10, 10, Set.of(),
                "approval:ip12-5-drill", "role:project-owner", "role:release-operator",
                "role:backend-owner", "metric:journey.search.shadow",
                NOW.minusSeconds(60), NOW.plusSeconds(3600),
                100, 1, 2, 8, Duration.ofMillis(200), Duration.ofMillis(300));
        var killSwitch = new TestMutableSearchShadowKillSwitch();
        killSwitch.enable();
        var metrics = new InMemorySearchShadowMetricSink();
        try (var executor = new ProductionShadowTaskExecutor(ProductionShadowResourcePolicyV1.approvedInitialPilot())) {
            var gate = new ProductionSearchShadowOperationalGate(
                    config,
                    killSwitch,
                    key -> false,
                    new ProductionSearchShadowSamplingGate(10),
                    executor,
                    metrics,
                    Clock.fixed(NOW, ZoneOffset.UTC));
            var decision = gate.dispatch(Optional.of(findIncludedHash()),
                    () -> { throw new AssertionError("must not dispatch"); }, ignored -> { });
            assertThat(decision.reason()).isEqualTo(ProductionSearchShadowActivationReason.EMPTY_ALLOWLIST);
        }
    }

    private static com.jc.intelligence.wiring.search.v1.SearchShadowHookRequestV1<PageResponse<PostDtos.Summary>> request() {
        var response = new PageResponse<>(List.of(new PostDtos.Summary(
                1L, "Seoul cafe", "kr-seoul", "Seoul", null, 0L, 0L, 0L,
                new PostDtos.Author(2L, "author", null), NOW)), 0, 20, 1L, 1, true);
        var factory = new DefaultExploreShadowHookRequestFactory(() -> new ExploreShadowRequestContext(
                "request:ip12", "correlation:ip12", null, NOW, NOW,
                new ProducerBuildId("ip12-disable-drill")));
        return factory.create("cafe", "kr-seoul", PageRequest.of(0, 20), response);
    }

    private static String findIncludedHash() {
        var sampling = new ProductionSearchShadowSamplingGate(10);
        for (long id = 1; id < 200_000; id++) {
            String hash = SecurityContextProductionInternalAccountHashResolver.sha256("user:" + id);
            if (sampling.decide(hash).included()) {
                return hash;
            }
        }
        throw new AssertionError("no deterministic 10 BPS fixture found");
    }

    private static void awaitMetric(InMemorySearchShadowMetricSink metrics, SearchShadowMetricName name)
            throws InterruptedException {
        for (int attempt = 0; attempt < 200 && metrics.value(name) == 0; attempt++) {
            Thread.sleep(5);
        }
        assertThat(metrics.value(name)).isGreaterThan(0L);
    }
}
