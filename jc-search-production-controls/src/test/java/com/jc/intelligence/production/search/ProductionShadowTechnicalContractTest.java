package com.jc.intelligence.production.search;

import com.jc.intelligence.compat.search.explore.v1.LegacyExploreRequestView;
import com.jc.intelligence.integration.search.v1.SearchShadowContextV1;
import com.jc.intelligence.integration.search.v1.SearchShadowRuntimeInputStatus;
import com.jc.intelligence.production.search.v1.AllowlistedInternalCohortSelector;
import com.jc.intelligence.production.search.v1.DisabledSearchShadowKillSwitch;
import com.jc.intelligence.production.search.v1.EmptyProductionShadowCohortSelector;
import com.jc.intelligence.production.search.v1.InMemorySearchProjectionStore;
import com.jc.intelligence.production.search.v1.InMemorySearchShadowEvidenceSink;
import com.jc.intelligence.production.search.v1.InMemorySearchShadowMetricSink;
import com.jc.intelligence.production.search.v1.PrivacySafeSearchShadowEvidenceV1;
import com.jc.intelligence.production.search.v1.ProductionProjectionSearchRuntimeFactory;
import com.jc.intelligence.production.search.v1.ProductionShadowDispatchRequestV1;
import com.jc.intelligence.production.search.v1.ProductionShadowDispatchStatus;
import com.jc.intelligence.production.search.v1.ProductionShadowResourcePolicyV1;
import com.jc.intelligence.production.search.v1.ProductionShadowSamplingAuthorization;
import com.jc.intelligence.production.search.v1.ProductionShadowTaskExecutor;
import com.jc.intelligence.production.search.v1.ProductionShadowTechnicalGate;
import com.jc.intelligence.production.search.v1.ProjectionDeletionStatus;
import com.jc.intelligence.production.search.v1.ProjectionExploreRuntimeInputProviderFactory;
import com.jc.intelligence.production.search.v1.ProjectionModerationStatus;
import com.jc.intelligence.production.search.v1.ProjectionPublicationStatus;
import com.jc.intelligence.production.search.v1.ProjectionVisibilityStatus;
import com.jc.intelligence.production.search.v1.PropertyBackedSearchShadowKillSwitch;
import com.jc.intelligence.production.search.v1.SearchDocumentEligibilityPolicyV1;
import com.jc.intelligence.production.search.v1.SearchDocumentProjectorV1;
import com.jc.intelligence.production.search.v1.SearchDocumentSourceV1;
import com.jc.intelligence.production.search.v1.SearchProductionContractIds;
import com.jc.intelligence.production.search.v1.SearchProjectionAvailabilityStatus;
import com.jc.intelligence.production.search.v1.SearchProjectionProjectorService;
import com.jc.intelligence.production.search.v1.SearchProjectionQueryV1;
import com.jc.intelligence.production.search.v1.SearchProjectionTextNormalizer;
import com.jc.intelligence.production.search.v1.SearchProjectionWriteStatus;
import com.jc.intelligence.production.search.v1.SearchShadowEvidenceStatus;
import com.jc.intelligence.production.search.v1.SearchShadowKillState;
import com.jc.intelligence.production.search.v1.SearchShadowMetricName;
import com.jc.intelligence.production.search.v1.TestMutableSearchShadowKillSwitch;
import com.jc.intelligence.runtime.search.v1.SearchRuntimeStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class ProductionShadowTechnicalContractTest {
    private static final Instant TIME = Instant.parse("2026-07-20T06:00:00Z");
    private static int assertions;
    private ProductionShadowTechnicalContractTest() { }

    public static void main(String[] args) throws Exception {
        projectionAndEligibility();
        projectionQueryAndRuntime();
        runtimeInputProvider();
        killSwitchCohortAndSampling();
        resourceGateAndEmergencyDrill();
        observabilityAndEvidence();
        privacyAndArchitectureContracts();
        System.out.println("IP-11.5 production shadow technical assertions: " + assertions + " PASS");
    }

    private static void projectionAndEligibility() {
        var store = new InMemorySearchProjectionStore();
        var service = service(store);
        var eligible = source(1, 1, "Seoul Cafe", "quiet rooftop cafe", ProjectionVisibilityStatus.PUBLIC,
                ProjectionPublicationStatus.PUBLISHED, ProjectionModerationStatus.ELIGIBLE,
                ProjectionDeletionStatus.ACTIVE, false, TIME.minusSeconds(10));
        var inserted = service.apply(eligible);
        check(inserted.status() == SearchProjectionWriteStatus.INSERTED, "eligible source inserted");
        check(store.size() == 1, "projection count one");
        var projection = store.findBySourcePostId(1).orElseThrow();
        check(projection.documentId().equals("post:1"), "document ID binds source");
        check(projection.regionReference().equals("kr-seoul"), "region reference retained");
        check(projection.normalizedTitleTerms().equals(List.of("seoul", "cafe")), "title terms deterministic");
        check(projection.normalizedBodyTerms().equals(List.of("quiet", "rooftop", "cafe")), "body terms deterministic");
        check(projection.deterministicContentHash().matches("[0-9a-f]{64}"), "content hash shape");
        check(service.apply(eligible).status() == SearchProjectionWriteStatus.UNCHANGED, "idempotent replay unchanged");
        var same = new SearchDocumentProjectorV1().project(eligible, TIME);
        check(same.deterministicContentHash().equals(projection.deterministicContentHash()), "projector deterministic");

        var updated = source(1, 2, "Seoul Museum", "night exhibition", ProjectionVisibilityStatus.PUBLIC,
                ProjectionPublicationStatus.PUBLISHED, ProjectionModerationStatus.ELIGIBLE,
                ProjectionDeletionStatus.ACTIVE, false, TIME.minusSeconds(5));
        check(service.apply(updated).status() == SearchProjectionWriteStatus.UPDATED, "source update applied");
        check(store.findBySourcePostId(1).orElseThrow().normalizedTitleTerms().contains("museum"), "updated terms visible");
        check(service.apply(eligible).status() == SearchProjectionWriteStatus.STALE_IGNORED, "stale source ignored");

        var conflicting = source(1, 2, "Conflicting Hash", "different body", ProjectionVisibilityStatus.PUBLIC,
                ProjectionPublicationStatus.PUBLISHED, ProjectionModerationStatus.ELIGIBLE,
                ProjectionDeletionStatus.ACTIVE, false, TIME.minusSeconds(5));
        check(service.apply(conflicting).status() == SearchProjectionWriteStatus.HASH_MISMATCH_REJECTED,
                "same version hash mismatch rejected");

        check(service.apply(source(1, 3, "Private", "body", ProjectionVisibilityStatus.NON_PUBLIC,
                ProjectionPublicationStatus.PUBLISHED, ProjectionModerationStatus.ELIGIBLE,
                ProjectionDeletionStatus.ACTIVE, false, TIME)).status() == SearchProjectionWriteStatus.REMOVED,
                "private transition removes projection");
        check(store.size() == 0, "private projection absent");
        check(service.apply(source(2, 1, "Draft", "body", ProjectionVisibilityStatus.PUBLIC,
                ProjectionPublicationStatus.DRAFT, ProjectionModerationStatus.ELIGIBLE,
                ProjectionDeletionStatus.ACTIVE, false, TIME)).status() == SearchProjectionWriteStatus.SOURCE_MISSING,
                "draft remains absent");
        check(service.apply(source(3, 1, "Deleted", "body", ProjectionVisibilityStatus.PUBLIC,
                ProjectionPublicationStatus.DELETED, ProjectionModerationStatus.ELIGIBLE,
                ProjectionDeletionStatus.DELETED, false, TIME)).status() == SearchProjectionWriteStatus.SOURCE_MISSING,
                "deleted remains absent");
        check(service.apply(source(4, 1, "Blocked", "body", ProjectionVisibilityStatus.PUBLIC,
                ProjectionPublicationStatus.PUBLISHED, ProjectionModerationStatus.BLOCKED,
                ProjectionDeletionStatus.ACTIVE, false, TIME)).status() == SearchProjectionWriteStatus.SOURCE_MISSING,
                "moderation block remains absent");
        check(service.apply(source(5, 1, "Unknown", "body", ProjectionVisibilityStatus.UNKNOWN,
                ProjectionPublicationStatus.UNKNOWN, ProjectionModerationStatus.UNKNOWN,
                ProjectionDeletionStatus.UNKNOWN, false, TIME)).status() == SearchProjectionWriteStatus.SOURCE_MISSING,
                "unknown authority fail closed");
        check(service.apply(source(6, 1, "Excluded", "body", ProjectionVisibilityStatus.PUBLIC,
                ProjectionPublicationStatus.PUBLISHED, ProjectionModerationStatus.ELIGIBLE,
                ProjectionDeletionStatus.ACTIVE, true, TIME)).status() == SearchProjectionWriteStatus.SOURCE_MISSING,
                "operational exclusion fail closed");
        check(service.removeMissingSource(99).status() == SearchProjectionWriteStatus.SOURCE_MISSING,
                "source missing removal safe");
        check(SearchProjectionTextNormalizer.terms("  Café CAFÉ 서울! ", 10).size() == 2,
                "term normalizer NFKC and deduplicates");
    }

    private static void projectionQueryAndRuntime() {
        var store = new InMemorySearchProjectionStore();
        var service = service(store);
        service.apply(source(10, 1, "Seoul cafe", "rooftop view", ProjectionVisibilityStatus.PUBLIC,
                ProjectionPublicationStatus.PUBLISHED, ProjectionModerationStatus.ELIGIBLE,
                ProjectionDeletionStatus.ACTIVE, false, TIME.minusSeconds(20)));
        service.apply(source(11, 1, "Busan beach", "night cafe", ProjectionVisibilityStatus.PUBLIC,
                ProjectionPublicationStatus.PUBLISHED, ProjectionModerationStatus.ELIGIBLE,
                ProjectionDeletionStatus.ACTIVE, false, TIME.minusSeconds(10), 2L, "kr-busan", "place:2"));
        service.apply(source(12, 1, "Seoul museum", "history", ProjectionVisibilityStatus.PUBLIC,
                ProjectionPublicationStatus.PUBLISHED, ProjectionModerationStatus.ELIGIBLE,
                ProjectionDeletionStatus.ACTIVE, false, TIME.minusSeconds(5)));
        var result = store.query(new SearchProjectionQueryV1(List.of("cafe"), null, 10,
                SearchProductionContractIds.PROJECTION_SCHEMA, SearchProductionContractIds.ELIGIBILITY_POLICY,
                TIME, Duration.ofDays(1)));
        check(result.status() == SearchProjectionAvailabilityStatus.AVAILABLE, "projection query available");
        check(result.documents().size() == 2, "term query bounded candidates");
        check(result.documents().get(0).sourcePostId() == 10, "title match outranks body match");
        var region = store.query(new SearchProjectionQueryV1(List.of(), "kr-busan", 10,
                SearchProductionContractIds.PROJECTION_SCHEMA, SearchProductionContractIds.ELIGIBILITY_POLICY,
                TIME, Duration.ofDays(1)));
        check(region.documents().size() == 1 && region.documents().get(0).sourcePostId() == 11,
                "region projection query exact");
        var stale = store.query(new SearchProjectionQueryV1(List.of("cafe"), null, 10,
                SearchProductionContractIds.PROJECTION_SCHEMA, SearchProductionContractIds.ELIGIBILITY_POLICY,
                TIME.plus(Duration.ofDays(2)), Duration.ofHours(1)));
        check(stale.status() == SearchProjectionAvailabilityStatus.STALE, "stale projection fails closed");
        var limited = store.query(new SearchProjectionQueryV1(List.of(), null, 1,
                SearchProductionContractIds.PROJECTION_SCHEMA, SearchProductionContractIds.ELIGIBILITY_POLICY,
                TIME, Duration.ofDays(1)));
        check(limited.documents().size() == 1, "candidate count bounded");
        store.setAvailable(false);
        check(store.query(new SearchProjectionQueryV1(List.of(), null, 10,
                SearchProductionContractIds.PROJECTION_SCHEMA, SearchProductionContractIds.ELIGIBILITY_POLICY,
                TIME, Duration.ofDays(1))).status() == SearchProjectionAvailabilityStatus.UNAVAILABLE,
                "store unavailable fail closed");
        store.setAvailable(true);

        var factory = new ProjectionExploreRuntimeInputProviderFactory(100);
        var provider = factory.create(new LegacyExploreRequestView("cafe", null, 0, 20, List.of(), Map.of()),
                new SearchShadowContextV1("request:ip115-runtime", "correlation:ip115-runtime",
                        "session:ip115-runtime", TIME));
        var input = provider.provide(null);
        check(input.status() == SearchShadowRuntimeInputStatus.AVAILABLE, "runtime input available");
        var runtime = ProductionProjectionSearchRuntimeFactory.create(store, Duration.ofDays(1));
        var runtimeResult = runtime.execute(input.executionRequest());
        check(runtimeResult.status() == SearchRuntimeStatus.SUCCESS, "Search Runtime executes projection source");
        check(runtimeResult.snapshot().items().size() == 2, "runtime returns projection candidates");
        check(runtimeResult.snapshot().items().stream().noneMatch(x -> x.candidate().entityRef().value().contains("user")),
                "runtime output has no user identity");
        store.setAvailable(false);
        check(runtime.execute(input.executionRequest()).status() == SearchRuntimeStatus.DEPENDENCY_UNAVAILABLE,
                "runtime provider unavailability isolated");
    }

    private static void runtimeInputProvider() {
        var factory = new ProjectionExploreRuntimeInputProviderFactory(100);
        var context = new SearchShadowContextV1("request:ip115-provider", "correlation:ip115-provider",
                "session:ip115-provider", TIME);
        var request = new LegacyExploreRequestView("  Cafe  ", "KR-Seoul", 0, 20, List.of(), Map.of());
        var input = factory.create(request, context).provide(null);
        check(input.status() == SearchShadowRuntimeInputStatus.AVAILABLE, "provider accepts first page");
        check(input.executionRequest().retrievalStrategyVersion().equals(SearchProductionContractIds.RETRIEVAL_STRATEGY),
                "provider binds projection retrieval version");
        check(input.executionRequest().maximumCandidateCount() == 100, "provider candidate limit");
        check(input.executionRequest().searchRequest().filters().get(0).values().equals(List.of("kr-seoul")),
                "provider canonical region");
        check(factory.create(new LegacyExploreRequestView(null, null, 1, 20, List.of(), Map.of()), context)
                .provide(null).status() == SearchShadowRuntimeInputStatus.UNSUPPORTED, "offset unsupported");
        check(factory.create(new LegacyExploreRequestView(null, null, -1, 20, List.of(), Map.of()), context)
                .provide(null).status() == SearchShadowRuntimeInputStatus.INVALID, "invalid page rejected");
        check(factory.create(new LegacyExploreRequestView(null, null, 0, 20, List.of(), Map.of("x", List.of("y"))), context)
                .provide(null).status() == SearchShadowRuntimeInputStatus.UNSUPPORTED, "unsupported filter rejected");
        check(input.runtimeInputFingerprint().matches("[0-9a-f]{64}"), "provider fingerprint safe");
    }

    private static void killSwitchCohortAndSampling() {
        check(new DisabledSearchShadowKillSwitch().killed(), "default kill switch killed");
        check(new PropertyBackedSearchShadowKillSwitch(() -> null, () -> true).killed(), "missing switch killed");
        check(new PropertyBackedSearchShadowKillSwitch(() -> " ", () -> true).killed(), "blank switch killed");
        check(new PropertyBackedSearchShadowKillSwitch(() -> "garbage", () -> true).killed(), "malformed switch killed");
        check(new PropertyBackedSearchShadowKillSwitch(() -> "enabled", () -> false).killed(), "approval absent killed");
        check(new PropertyBackedSearchShadowKillSwitch(() -> "enabled", () -> true).state() == SearchShadowKillState.ENABLED,
                "explicit approved switch enabled");
        var mutable = new TestMutableSearchShadowKillSwitch();
        check(mutable.killed(), "mutable starts killed"); mutable.enable(); check(!mutable.killed(), "mutable enables");
        mutable.kill(); check(mutable.killed(), "mutable repeated kill safe"); mutable.kill(); check(mutable.killed(), "mutable idempotent");

        String internalHash = SearchDocumentProjectorV1.sha256("internal-fixture");
        check(!new EmptyProductionShadowCohortSelector().includes(internalHash), "default cohort empty");
        check(new AllowlistedInternalCohortSelector(Set.of(internalHash), true).includes(internalHash),
                "approved internal fixture accepted");
        check(!new AllowlistedInternalCohortSelector(Set.of(internalHash), false).includes(internalHash),
                "approval absent cohort empty");
        check(!new AllowlistedInternalCohortSelector(Set.of("raw-user-id"), true).includes(internalHash),
                "malformed allowlist empty");

        var production = ProductionShadowSamplingAuthorization.productionDefault();
        check(production.effectiveBasisPoints() == 0, "production effective sample zero");
        check(!production.decide("correlation:stable").included(), "zero sample dispatch zero");
        var test = ProductionShadowSamplingAuthorization.technicalTestOnly(10_000);
        check(test.effectiveBasisPoints() == 10_000, "test override explicit");
        check(test.decide("correlation:stable").included(), "test sample included");
        check(test.decide("correlation:stable").bucket() == test.decide("correlation:stable").bucket(),
                "sampler deterministic");
        expectFailure(() -> new ProductionShadowSamplingAuthorization(-1, false, false));
        expectFailure(() -> new ProductionShadowSamplingAuthorization(10_001, false, false));
        expectFailure(() -> new ProductionShadowSamplingAuthorization(10, true, false));
    }

    private static void resourceGateAndEmergencyDrill() throws Exception {
        var policy = ProductionShadowResourcePolicyV1.provisional();
        check(policy.approvalStatus().equals("PROVISIONAL_NOT_APPROVED"), "resource policy not approved");
        check(policy.maximumSampleBasisPoints() == 100, "proposed ceiling bounded");
        check(policy.maximumCandidateCount() == 100, "candidate budget bounded");
        Object legacy = new Object();
        String internalHash = SearchDocumentProjectorV1.sha256("internal-fixture");
        var metrics = new InMemorySearchShadowMetricSink();
        var kill = new TestMutableSearchShadowKillSwitch();
        var cohort = new AllowlistedInternalCohortSelector(Set.of(internalHash), true);
        var sideEffects = new AtomicInteger();
        try (var executor = new ProductionShadowTaskExecutor(policy)) {
            var gate = new ProductionShadowTechnicalGate<Object>(kill, cohort,
                    ProductionShadowSamplingAuthorization.technicalTestOnly(10_000), executor, metrics);
            var killed = gate.dispatch(new ProductionShadowDispatchRequestV1<>(legacy, "correlation:drill", internalHash,
                    sideEffects::incrementAndGet));
            check(killed.status() == ProductionShadowDispatchStatus.KILLED, "drill starts killed");
            check(killed.legacyResponse() == legacy, "killed preserves legacy identity");
            check(sideEffects.get() == 0, "killed side effect zero");
            kill.enable();
            CountDownLatch done = new CountDownLatch(1);
            var submitted = gate.dispatch(new ProductionShadowDispatchRequestV1<>(legacy, "correlation:drill", internalHash,
                    () -> { sideEffects.incrementAndGet(); done.countDown(); }));
            check(submitted.status() == ProductionShadowDispatchStatus.SUBMITTED, "technical fixture submitted");
            check(submitted.legacyResponse() == legacy, "submitted preserves legacy identity");
            check(done.await(2, TimeUnit.SECONDS), "asynchronous shadow task completed");
            check(sideEffects.get() == 1, "shadow executed once");
            kill.kill();
            var afterKill = gate.dispatch(new ProductionShadowDispatchRequestV1<>(legacy, "correlation:drill-2", internalHash,
                    sideEffects::incrementAndGet));
            check(afterKill.status() == ProductionShadowDispatchStatus.KILLED, "emergency switch kills subsequent dispatch");
            Thread.sleep(30);
            check(sideEffects.get() == 1, "provider/runtime/comparison side effects remain zero after kill");
            check(metrics.value(SearchShadowMetricName.KILLED) == 2, "killed metric counted");
            check(metrics.value(SearchShadowMetricName.DISPATCHED) == 1, "dispatch metric counted");
        }

        var nonAllow = new AtomicInteger();
        try (var executor = new ProductionShadowTaskExecutor(policy)) {
            var gate = new ProductionShadowTechnicalGate<Object>(
                    new PropertyBackedSearchShadowKillSwitch(() -> "enabled", () -> true),
                    new EmptyProductionShadowCohortSelector(), ProductionShadowSamplingAuthorization.technicalTestOnly(10_000),
                    executor, new InMemorySearchShadowMetricSink());
            var receipt = gate.dispatch(new ProductionShadowDispatchRequestV1<>(legacy, "correlation:cohort", internalHash,
                    nonAllow::incrementAndGet));
            check(receipt.status() == ProductionShadowDispatchStatus.COHORT_REJECTED, "empty cohort dispatch zero");
            check(nonAllow.get() == 0, "cohort rejection side effect zero");
        }

        var zero = new AtomicInteger();
        try (var executor = new ProductionShadowTaskExecutor(policy)) {
            var gate = new ProductionShadowTechnicalGate<Object>(
                    new PropertyBackedSearchShadowKillSwitch(() -> "enabled", () -> true), cohort,
                    ProductionShadowSamplingAuthorization.productionDefault(), executor,
                    new InMemorySearchShadowMetricSink());
            var receipt = gate.dispatch(new ProductionShadowDispatchRequestV1<>(legacy, "correlation:sample", internalHash,
                    zero::incrementAndGet));
            check(receipt.status() == ProductionShadowDispatchStatus.NOT_SAMPLED, "production sample zero blocks dispatch");
            check(receipt.effectiveSampleBasisPoints() == 0, "receipt effective sample zero");
            check(zero.get() == 0, "sample zero side effect zero");
        }

        try (var executor = new ProductionShadowTaskExecutor(policy)) {
            var failingMetrics = new com.jc.intelligence.production.search.v1.SearchShadowMetricSink() {
                @Override public void increment(SearchShadowMetricName name, Map<String,String> tags) { throw new IllegalStateException("metric failure"); }
                @Override public void recordDuration(SearchShadowMetricName name, Duration duration, Map<String,String> tags) { throw new IllegalStateException("metric failure"); }
                @Override public void recordGauge(SearchShadowMetricName name, long value, Map<String,String> tags) { throw new IllegalStateException("metric failure"); }
            };
            var failClosed = new ProductionShadowTechnicalGate<Object>(
                    () -> { throw new IllegalStateException("switch failure"); }, cohort,
                    ProductionShadowSamplingAuthorization.technicalTestOnly(10_000), executor, failingMetrics);
            var receipt = failClosed.dispatch(new ProductionShadowDispatchRequestV1<>(legacy, "correlation:failure", internalHash,
                    sideEffects::incrementAndGet));
            check(receipt.status() == ProductionShadowDispatchStatus.KILLED, "control failure fails closed");
            check(receipt.legacyResponse() == legacy, "metric/control failure preserves legacy identity");
        }

        var tiny = new ProductionShadowResourcePolicyV1(1,1,1,Duration.ofMillis(1),Duration.ofMillis(50),
                Duration.ofMillis(60),2,Duration.ofMillis(100),10,10,Duration.ofMillis(100),
                "PROVISIONAL_NOT_APPROVED");
        CountDownLatch block = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        try (var executor = new ProductionShadowTaskExecutor(tiny)) {
            check(executor.submit(() -> { started.countDown(); await(block); }) == ProductionShadowDispatchStatus.SUBMITTED, "executor first accepted");
            check(awaitStarted(started), "executor worker started");
            check(executor.submit(() -> await(block)) == ProductionShadowDispatchStatus.SUBMITTED, "executor queue accepted");
            check(executor.submit(() -> { }) == ProductionShadowDispatchStatus.QUEUE_FULL, "executor queue full isolated");
            block.countDown();
        }
    }

    private static void observabilityAndEvidence() {
        var metrics = new InMemorySearchShadowMetricSink();
        metrics.increment(SearchShadowMetricName.COMPLETED, Map.of("status", "success"));
        metrics.recordDuration(SearchShadowMetricName.RUNTIME_LATENCY, Duration.ofMillis(12), Map.of("bucket", "lt_50ms"));
        metrics.recordGauge(SearchShadowMetricName.QUEUE_DEPTH, 2, Map.of("stage", "technical"));
        check(metrics.value(SearchShadowMetricName.COMPLETED) == 1, "success metric");
        check(metrics.value(SearchShadowMetricName.RUNTIME_LATENCY) == 1, "timer metric bounded");
        check(metrics.value(SearchShadowMetricName.QUEUE_DEPTH) == 2, "gauge metric");
        expectFailure(() -> metrics.increment(SearchShadowMetricName.COMPLETED, Map.of("user_id", "123")));
        expectFailure(() -> metrics.increment(SearchShadowMetricName.COMPLETED, Map.of("query", "cafe")));
        expectFailure(() -> metrics.increment(SearchShadowMetricName.COMPLETED,
                Map.of("a","a","b","b","c","c","d","d","e","e")));

        var evidence = new PrivacySafeSearchShadowEvidenceV1(TIME, "run:opaque-ip115-001",
                SearchProductionContractIds.PROJECTION_SCHEMA.value(), SearchProductionContractIds.ELIGIBILITY_POLICY.value(),
                SearchShadowEvidenceStatus.COMPLETED, "lt_50ms", "count_1_10", "overlap_high",
                "divergence_low", "fresh_lt_1h", "completed");
        var sink = new InMemorySearchShadowEvidenceSink(2);
        sink.record(evidence); sink.record(evidence); sink.record(evidence);
        check(sink.records().size() == 2, "evidence recorder bounded");
        String text = sink.records().toString().toLowerCase();
        check(!text.contains("query") && !text.contains("user") && !text.contains("post:"),
                "evidence excludes raw query identity and post IDs");
        expectFailure(() -> new PrivacySafeSearchShadowEvidenceV1(TIME, "run:opaque-ip115-001",
                "bad-schema", SearchProductionContractIds.ELIGIBILITY_POLICY.value(), SearchShadowEvidenceStatus.COMPLETED,
                "lt_50ms", "count_1_10", "overlap_high", "divergence_low", "fresh_lt_1h", "completed"));
    }

    private static void privacyAndArchitectureContracts() {
        check(SearchProductionContractIds.TECHNICAL_CONTROLS.value().equals("ip-11-5-production-shadow-technical-controls-v1"), "technical contract ID");
        check(SearchProductionContractIds.PROJECTION_SCHEMA.value().equals("search-document-projection-v1"), "projection schema ID");
        check(SearchProductionContractIds.ELIGIBILITY_POLICY.value().equals("search-document-eligibility-v1"), "eligibility policy ID");
        check(ProductionShadowSamplingAuthorization.productionDefault().effectiveBasisPoints() == 0,
                "production remains zero bps");
        check(new DisabledSearchShadowKillSwitch().killed(), "production remains killed");
        for (SearchShadowMetricName metric : SearchShadowMetricName.values()) {
            check(metric.wireValue().startsWith("shadow."), "metric namespace bounded " + metric.name());
            check(!metric.wireValue().matches(".*(query|user|session|jwt|post|document).*"),
                    "metric name omits sensitive dimensions " + metric.name());
        }
    }

    private static SearchProjectionProjectorService service(InMemorySearchProjectionStore store) {
        return new SearchProjectionProjectorService(new SearchDocumentEligibilityPolicyV1(Duration.ofDays(7)),
                new SearchDocumentProjectorV1(), store, Clock.fixed(TIME, ZoneOffset.UTC));
    }
    private static SearchDocumentSourceV1 source(long id,long version,String title,String body,
            ProjectionVisibilityStatus visibility,ProjectionPublicationStatus publication,
            ProjectionModerationStatus moderation,ProjectionDeletionStatus deletion,boolean operationalExcluded,
            Instant updatedAt) {
        return source(id,version,title,body,visibility,publication,moderation,deletion,operationalExcluded,updatedAt,
                1L,"kr-seoul","place:1");
    }
    private static SearchDocumentSourceV1 source(long id,long version,String title,String body,
            ProjectionVisibilityStatus visibility,ProjectionPublicationStatus publication,
            ProjectionModerationStatus moderation,ProjectionDeletionStatus deletion,boolean operationalExcluded,
            Instant updatedAt,Long regionId,String regionReference,String placeReference) {
        return new SearchDocumentSourceV1(id,version,regionId,regionReference,placeReference,title,body,visibility,
                publication,moderation,deletion,operationalExcluded,updatedAt);
    }
    private static void await(CountDownLatch latch) { try { latch.await(2,TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
    private static boolean awaitStarted(CountDownLatch latch) { try { return latch.await(2,TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; } }
    private static void check(boolean condition,String message) { assertions++; if(!condition) throw new AssertionError(message); }
    private static void expectFailure(Runnable task) { assertions++; try { task.run(); throw new AssertionError("expected failure"); } catch (IllegalArgumentException expected) { } }
}
