package com.jc.backend.search.shadow.production;

import com.jc.backend.common.PageResponse;
import com.jc.backend.post.PostDtos;
import com.jc.backend.search.shadow.DefaultExploreShadowHookRequestFactory;
import com.jc.backend.search.shadow.ExploreShadowRequestContext;
import com.jc.intelligence.contract.v1.version.ProducerBuildId;
import com.jc.intelligence.integration.search.v1.SearchShadowIntegrationBoundary;
import com.jc.intelligence.integration.search.v1.SearchShadowMode;
import com.jc.intelligence.integration.search.v1.SearchShadowPolicyV1;
import com.jc.intelligence.contract.v1.version.PolicyVersion;
import com.jc.intelligence.production.search.v1.AllowlistedInternalCohortSelector;
import com.jc.intelligence.production.search.v1.InMemorySearchProjectionStore;
import com.jc.intelligence.production.search.v1.InMemorySearchShadowEvidenceSink;
import com.jc.intelligence.production.search.v1.InMemorySearchShadowMetricSink;
import com.jc.intelligence.production.search.v1.ProductionProjectionSearchRuntimeFactory;
import com.jc.intelligence.production.search.v1.ProductionShadowDispatchStatus;
import com.jc.intelligence.production.search.v1.ProductionShadowResourcePolicyV1;
import com.jc.intelligence.production.search.v1.ProductionShadowTaskCompletionStatus;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class IP12DirectOperationalContract {
    private static int assertions;
    private static final Instant NOW = Instant.parse("2026-07-20T00:00:00Z");

    public static void main(String[] args) throws Exception {
        defaultsAndValidation();
        samplingBoundary();
        gateAndKillSwitch();
        resourceIsolation();
        legacyAuthorityAndActualRuntime();
        System.out.println("IP-12 direct operational contract: " + assertions + " PASS");
    }

    private static void defaultsAndValidation() {
        ProductionSearchShadowProperties p = new ProductionSearchShadowProperties();
        check(!p.isEnabled(), "enabled defaults false");
        check(p.isKillSwitch(), "kill switch defaults true");
        check(p.getSamplingBps() == 0, "sampling defaults zero");
        check(p.getMaxApprovedSamplingBps() == 10, "approved ceiling ten");
        check(p.getAllowlistHashes().isEmpty(), "allowlist defaults empty");
        ProductionSearchShadowRuntimeConfig c = ProductionSearchShadowPropertiesValidator.validate(p);
        check(c.effectiveSamplingBps() == 0 && !c.dispatchConfigured(), "default effective disabled");
        check(!c.operationalInputsPresent(), "default operational inputs absent");
        for (int accepted : new int[]{0, 1, 9, 10}) {
            p = enabledProperties(accepted, false, List.of(hash("user:" + (accepted + 1))));
            check(ProductionSearchShadowPropertiesValidator.validate(p).configuredSamplingBps() == accepted,
                    "accepted sampling " + accepted);
        }
        for (int rejected : new int[]{-1, 11, 50, 100, Integer.MAX_VALUE}) {
            ProductionSearchShadowProperties invalidSampling =
                    enabledProperties(rejected, false, List.of(hash("user:1")));
            expectFailure(() -> ProductionSearchShadowPropertiesValidator.validate(invalidSampling),
                    "rejected sampling " + rejected);
        }
        ProductionSearchShadowProperties missingApproval = enabledProperties(10, false, List.of(hash("user:1")));
        missingApproval.setActivationApprovalRef("");
        expectFailure(() -> ProductionSearchShadowPropertiesValidator.validate(missingApproval),
                "activation approval required");
        ProductionSearchShadowProperties rawIdentity = enabledProperties(10, false, List.of(" user-1 "));
        expectFailure(() -> ProductionSearchShadowPropertiesValidator.validate(rawIdentity), "raw identity rejected");
        String h = hash("user:1");
        p = enabledProperties(10, false, List.of(h.toUpperCase(java.util.Locale.ROOT)));
        check(ProductionSearchShadowPropertiesValidator.validate(p).allowlistHashes().contains(h),
                "hash case normalized");
        p = enabledProperties(10, true, List.of(h));
        check(ProductionSearchShadowPropertiesValidator.validate(p).effectiveSamplingBps() == 0,
                "kill switch forces zero");
    }

    private static void samplingBoundary() {
        String h = hash("user:777");
        var zero = new ProductionSearchShadowSamplingGate(0).decide(h);
        check(!zero.included() && zero.basisPoints() == 0, "zero bps always excludes");
        var ten = new ProductionSearchShadowSamplingGate(10).decide(h);
        check(ten.included() == (ten.bucket() < 10), "ten bps means ten of ten thousand buckets");
        check(ten.bucket() >= 0 && ten.bucket() < 10_000, "sampling denominator ten thousand");
        check(ten.equals(new ProductionSearchShadowSamplingGate(10).decide(h)), "sampling deterministic");
        expectFailure(() -> new ProductionSearchShadowSamplingGate(11), "eleven bps constructor rejected");
    }

    private static void gateAndKillSwitch() throws Exception {
        String included = findIncludedHash();
        var config = new ProductionSearchShadowRuntimeConfig(true, false, 10, 10, Set.of(included),
                "approval:ip12-5-direct", "role:project-owner", "role:release-operator",
                "role:backend-owner", "metric:journey.search.shadow",
                NOW.minusSeconds(60), NOW.plusSeconds(3600),
                100, 1, 2, 8, Duration.ofMillis(200), Duration.ofMillis(300));
        var kill = new TestMutableSearchShadowKillSwitch();
        kill.enable();
        var metrics = new InMemorySearchShadowMetricSink();
        AtomicInteger work = new AtomicInteger();
        try (var executor = new ProductionShadowTaskExecutor(ProductionShadowResourcePolicyV1.approvedInitialPilot())) {
            var gate = new ProductionSearchShadowOperationalGate(config, kill,
                    new AllowlistedInternalCohortSelector(Set.of(included), true),
                    new ProductionSearchShadowSamplingGate(10), executor, metrics, Clock.fixed(NOW, ZoneOffset.UTC));
            var accepted = gate.dispatch(Optional.of(included), work::incrementAndGet, ignored -> { });
            check(accepted.reason() == ProductionSearchShadowActivationReason.DISPATCHED,
                    "allowlisted sampled request submitted");
            await(() -> work.get() == 1, "submitted task ran");
            kill.kill();
            var blocked = gate.dispatch(Optional.of(included), work::incrementAndGet, ignored -> { });
            check(blocked.reason() == ProductionSearchShadowActivationReason.DISABLED_BY_KILL_SWITCH,
                    "kill switch precedence");
            Thread.sleep(20);
            check(work.get() == 1, "killed path has zero new work");
        }
        var disabledConfig = new ProductionSearchShadowRuntimeConfig(false, true, 0, 0, Set.of(),
                null, null, null, null, null, null, null,
                100, 1, 2, 8, Duration.ofMillis(200), Duration.ofMillis(300));
        AtomicInteger identityCalls = new AtomicInteger();
        try (var executor = new ProductionShadowTaskExecutor(ProductionShadowResourcePolicyV1.approvedInitialPilot())) {
            var disabledGate = new ProductionSearchShadowOperationalGate(disabledConfig, kill, key -> false,
                    new ProductionSearchShadowSamplingGate(0), executor, metrics, Clock.fixed(NOW, ZoneOffset.UTC));
            var blocked = disabledGate.dispatch(() -> {
                identityCalls.incrementAndGet();
                return Optional.of(included);
            }, work::incrementAndGet, ignored -> { });
            check(blocked.reason() == ProductionSearchShadowActivationReason.DISABLED_BY_CONFIGURATION,
                    "disabled configuration blocks before identity resolution");
            check(identityCalls.get() == 0, "disabled configuration resolves no identity");
        }

        var empty = new ProductionSearchShadowRuntimeConfig(true, false, 10, 10, Set.of(),
                "approval:ip12-5-direct", "role:project-owner", "role:release-operator",
                "role:backend-owner", "metric:journey.search.shadow",
                NOW.minusSeconds(60), NOW.plusSeconds(3600),
                100, 1, 2, 8, Duration.ofMillis(200), Duration.ofMillis(300));
        var emptyKill = new TestMutableSearchShadowKillSwitch();
        emptyKill.enable();
        try (var executor = new ProductionShadowTaskExecutor(ProductionShadowResourcePolicyV1.approvedInitialPilot())) {
            var gate = new ProductionSearchShadowOperationalGate(empty, emptyKill, key -> false,
                    new ProductionSearchShadowSamplingGate(10), executor, metrics, Clock.fixed(NOW, ZoneOffset.UTC));
            var blocked = gate.dispatch(Optional.of(included), () -> { throw new AssertionError(); }, ignored -> { });
            check(blocked.reason() == ProductionSearchShadowActivationReason.EMPTY_ALLOWLIST,
                    "empty cohort dispatch zero");
        }
    }

    private static void resourceIsolation() throws Exception {
        var policy = ProductionShadowResourcePolicyV1.approvedInitialPilot();
        check(policy.coreConcurrency() == 1 && policy.maxConcurrency() == 2 && policy.queueCapacity() == 8,
                "approved executor bounds");
        check(policy.runtimeTimeout().equals(Duration.ofMillis(200))
                && policy.hardCancellationTimeout().equals(Duration.ofMillis(300)), "approved timeout bounds");
        check(policy.maximumSampleBasisPoints() == 10 && policy.maximumCandidateCount() == 100,
                "approved sample candidate bounds");
        AtomicReference<ProductionShadowTaskCompletionStatus> completion = new AtomicReference<>();
        try (var executor = new ProductionShadowTaskExecutor(policy)) {
            long started = System.nanoTime();
            var status = executor.submitTimed(() -> {
                try { Thread.sleep(5_000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            }, Duration.ofMillis(20), Duration.ofMillis(40), result -> completion.set(result.status()));
            long submitMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
            check(status == ProductionShadowDispatchStatus.SUBMITTED, "timed task submitted");
            check(submitMs < 100, "request thread did not join completion");
            await(() -> completion.get() != null, "timeout completion observed");
            check(completion.get() == ProductionShadowTaskCompletionStatus.TIMED_OUT
                    || completion.get() == ProductionShadowTaskCompletionStatus.HARD_TIMEOUT,
                    "timeout bounded");
        }
    }

    private static void legacyAuthorityAndActualRuntime() throws Exception {
        String included = findIncludedHash();
        var store = new InMemorySearchProjectionStore();
        var projector = new SearchProjectionProjectorService(
                new SearchDocumentEligibilityPolicyV1(Duration.ofDays(7)), new SearchDocumentProjectorV1(),
                store, Clock.fixed(NOW, ZoneOffset.UTC));
        projector.apply(new SearchDocumentSourceV1(1L, 1L, 11L, "kr-seoul", "place:101",
                "Seoul cafe", "public journey", ProjectionVisibilityStatus.PUBLIC,
                ProjectionPublicationStatus.PUBLISHED, ProjectionModerationStatus.ELIGIBLE,
                ProjectionDeletionStatus.ACTIVE, false, NOW.minusSeconds(30)));
        var config = new ProductionSearchShadowRuntimeConfig(true, false, 10, 10, Set.of(included),
                "approval:ip12-5-direct", "role:project-owner", "role:release-operator",
                "role:backend-owner", "metric:journey.search.shadow",
                NOW.minusSeconds(60), NOW.plusSeconds(3600),
                100, 1, 2, 8, Duration.ofMillis(200), Duration.ofMillis(300));
        var kill = new TestMutableSearchShadowKillSwitch(); kill.enable();
        var metrics = new InMemorySearchShadowMetricSink();
        var evidence = new InMemorySearchShadowEvidenceSink(8);
        try (var executor = new ProductionShadowTaskExecutor(ProductionShadowResourcePolicyV1.approvedInitialPilot())) {
            var gate = new ProductionSearchShadowOperationalGate(config, kill,
                    new AllowlistedInternalCohortSelector(Set.of(included), true),
                    new ProductionSearchShadowSamplingGate(10), executor, metrics, Clock.fixed(NOW, ZoneOffset.UTC));
            var runtime = ProductionProjectionSearchRuntimeFactory.create(store, Duration.ofHours(24));
            var integration = new SearchShadowIntegrationBoundary<PageResponse<PostDtos.Summary>>(
                    new SearchShadowPolicyV1(SearchShadowMode.SHADOW_ENABLED,
                            new PolicyVersion("search-shadow-production-policy-v1"),
                            new PolicyVersion("search-shadow-comparison-policy-v1"), Duration.ofMillis(200), 10,
                            new ProducerBuildId("ip12-direct")), runtime,
                    new DirectProductionSearchShadowExecutionPort());
            var hook = new ProductionExploreSearchShadowHook(() -> Optional.of(included), gate, integration,
                    new ProjectionExploreRuntimeInputProviderFactory(100), metrics, evidence,
                    new ProductionSearchShadowOperationalLogger(), Clock.fixed(NOW, ZoneOffset.UTC));
            var response = new PageResponse<PostDtos.Summary>(List.of(new PostDtos.Summary(1L, "Seoul cafe",
                    "kr-seoul", "Seoul", null, 0, 0, 0, new PostDtos.Author(2L, "author", null), NOW)),
                    0, 20, 1L, 1, true);
            var request = new DefaultExploreShadowHookRequestFactory(() -> new ExploreShadowRequestContext(
                    "request:ip12", "correlation:ip12", null, NOW, NOW,
                    new ProducerBuildId("ip12-direct"))).create("cafe", "kr-seoul",
                    org.springframework.data.domain.PageRequest.of(0, 20), response);
            var receipt = hook.dispatch(request);
            check(receipt.status() == SearchShadowDispatchStatus.SUBMITTED, "actual Search runtime submitted");
            check(receipt.legacyResponse() == response, "legacy object identity preserved");
            await(() -> metrics.value(SearchShadowMetricName.COMPLETED) > 0, "actual Search runtime completed");
            check(response.items().size() == 1 && response.totalElements() == 1 && response.page() == 0,
                    "legacy body and page unchanged");
        }
    }

    private static ProductionSearchShadowProperties enabledProperties(int sample, boolean kill, List<String> hashes) {
        ProductionSearchShadowProperties p = new ProductionSearchShadowProperties();
        p.setEnabled(true); p.setKillSwitch(kill); p.setSamplingBps(sample); p.setAllowlistHashes(hashes);
        p.setActivationApprovalRef("approval:ip12-5-direct");
        p.setActivationApproverRef("role:project-owner");
        p.setActivationExecutorRef("role:release-operator");
        p.setRollbackOwnerRef("role:backend-owner");
        p.setMetricVerificationRef("metric:journey.search.shadow");
        p.setActivationWindowStart("2026-07-19T23:00:00Z");
        p.setActivationWindowEnd("2026-07-20T01:00:00Z");
        return p;
    }

    private static String findIncludedHash() {
        var gate = new ProductionSearchShadowSamplingGate(10);
        for (long id = 1; id < 500_000; id++) {
            String hash = hash("user:" + id);
            if (gate.decide(hash).included()) return hash;
        }
        throw new AssertionError("included hash not found");
    }

    private static String hash(String value) {
        return SecurityContextProductionInternalAccountHashResolver.sha256(value);
    }

    private static void await(Check condition, String name) throws Exception {
        for (int i = 0; i < 1_000 && !condition.ok(); i++) Thread.sleep(5);
        check(condition.ok(), name);
    }

    private static void expectFailure(Runnable action, String name) {
        try { action.run(); throw new AssertionError(name); }
        catch (IllegalArgumentException | IllegalStateException expected) { assertions++; }
    }

    private static void check(boolean value, String name) {
        if (!value) throw new AssertionError(name);
        assertions++;
    }

    @FunctionalInterface private interface Check { boolean ok(); }
}
