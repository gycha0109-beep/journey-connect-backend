package com.jc.intelligence.wiring.search;

import com.jc.intelligence.compat.search.explore.v1.LegacyExploreAuthorView;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreCompatibilityAdapter;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreCompatibilityContext;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreCompatibilityResult;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreItemView;
import com.jc.intelligence.compat.search.explore.v1.LegacyExplorePageView;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreRequestView;
import com.jc.intelligence.contract.support.WireValue;
import com.jc.intelligence.contract.v1.version.ContractId;
import com.jc.intelligence.contract.v1.version.PolicyVersion;
import com.jc.intelligence.contract.v1.version.ProducerBuildId;
import com.jc.intelligence.integration.search.v1.SearchIntegrationContractIds;
import com.jc.intelligence.integration.search.v1.SearchShadowAuthorityV1;
import com.jc.intelligence.integration.search.v1.SearchShadowComparisonEvidenceV1;
import com.jc.intelligence.integration.search.v1.SearchShadowComparisonMetricsV1;
import com.jc.intelligence.integration.search.v1.SearchShadowComparisonStatus;
import com.jc.intelligence.integration.search.v1.SearchShadowContextV1;
import com.jc.intelligence.integration.search.v1.SearchShadowFingerprintV1;
import com.jc.intelligence.integration.search.v1.SearchShadowIntegrationBoundary;
import com.jc.intelligence.integration.search.v1.SearchShadowIntegrationPort;
import com.jc.intelligence.integration.search.v1.SearchShadowPolicyV1;
import com.jc.intelligence.integration.search.v1.UnavailableSearchShadowRuntimeInputProvider;
import com.jc.intelligence.integration.search.v1.fixture.DirectTestSearchShadowExecutionPort;
import com.jc.intelligence.integration.search.v1.SearchShadowIntegrationResult;
import com.jc.intelligence.integration.search.v1.SearchShadowMismatchCode;
import com.jc.intelligence.integration.search.v1.SearchShadowMismatchV1;
import com.jc.intelligence.integration.search.v1.SearchShadowMode;
import com.jc.intelligence.integration.search.v1.SearchShadowRuntimeInputProvider;
import com.jc.intelligence.integration.search.v1.SearchShadowRuntimeInputStatus;
import com.jc.intelligence.integration.search.v1.SearchShadowSeverity;
import com.jc.intelligence.integration.search.v1.SearchShadowStatus;
import com.jc.intelligence.integration.search.v1.SearchShadowWarningCode;
import com.jc.intelligence.wiring.search.v1.BackendExploreShadowHookAdapter;
import com.jc.intelligence.wiring.search.v1.DefaultSearchShadowDispatcher;
import com.jc.intelligence.wiring.search.v1.DeterministicSearchShadowSampler;
import com.jc.intelligence.wiring.search.v1.FixedSearchShadowCircuitBreaker;
import com.jc.intelligence.wiring.search.v1.NoOpSearchShadowComparisonLogPort;
import com.jc.intelligence.wiring.search.v1.NoOpSearchShadowHook;
import com.jc.intelligence.wiring.search.v1.SearchShadowActivationGate;
import com.jc.intelligence.wiring.search.v1.SearchShadowActivationInputsV1;
import com.jc.intelligence.wiring.search.v1.SearchShadowCircuitState;
import com.jc.intelligence.wiring.search.v1.SearchShadowComparisonLogPort;
import com.jc.intelligence.wiring.search.v1.SearchShadowComparisonLogResultV1;
import com.jc.intelligence.wiring.search.v1.SearchShadowDispatchReceiptV1;
import com.jc.intelligence.wiring.search.v1.SearchShadowDispatchStatus;
import com.jc.intelligence.wiring.search.v1.SearchShadowExecutor;
import com.jc.intelligence.wiring.search.v1.SearchShadowExecutorResultV1;
import com.jc.intelligence.wiring.search.v1.SearchShadowExecutorStatus;
import com.jc.intelligence.wiring.search.v1.SearchShadowHookRequestV1;
import com.jc.intelligence.wiring.search.v1.SearchShadowLogStatus;
import com.jc.intelligence.wiring.search.v1.SearchShadowSamplingPolicyV1;
import com.jc.intelligence.wiring.search.v1.SearchShadowStructuredRecordV1;
import com.jc.intelligence.wiring.search.v1.SearchShadowWiringAuthorityV1;
import com.jc.intelligence.wiring.search.v1.SearchShadowWiringConfigV1;
import com.jc.intelligence.wiring.search.v1.SearchShadowWiringContractIds;
import com.jc.intelligence.wiring.search.v1.SearchShadowWiringMode;
import com.jc.intelligence.wiring.search.v1.fixture.SearchShadowWiringFixtureCaseV1;
import com.jc.intelligence.wiring.search.v1.fixture.SearchShadowWiringFixtureJsonCodecV1;
import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class SearchShadowWiringContractTest {
    private static final Instant TIME = Instant.parse("2026-07-19T15:00:00Z");
    private static final ProducerBuildId BUILD = new ProducerBuildId("ip7-test-build");
    private static int assertions;
    private SearchShadowWiringContractTest() { }

    public static void main(String[] args) throws Exception {
        fixtureAndWireContracts();
        configurationAndActivationContracts();
        deterministicSamplingContracts();
        executorCircuitAndBackpressureContracts();
        disabledNoOpAndAdapterContracts();
        controlledDispatchAndResponseAuthorityContracts();
        actualIp6BoundaryAndPagedResponseContracts();
        loggingPrivacyAndFailureIsolationContracts();
        determinismBoundaryAndNullSafetyContracts();
        architectureAndForbiddenDependencyContracts();
        System.out.println("IP-7 Search shadow wiring assertions: " + assertions + " PASS");
    }

    private static void fixtureAndWireContracts() throws IOException {
        List<Path> paths = fixturePaths();
        check(paths.size() == 16, "16 wiring fixtures exist");
        Set<String> scenarios = new HashSet<>();
        for (Path path : paths) {
            String json = Files.readString(path);
            SearchShadowWiringFixtureCaseV1 fixture = SearchShadowWiringFixtureJsonCodecV1.read(json);
            SearchShadowWiringFixtureCaseV1 round = SearchShadowWiringFixtureJsonCodecV1.read(
                    SearchShadowWiringFixtureJsonCodecV1.write(fixture));
            check(fixture.equals(round), "fixture round trip " + fixture.scenario());
            check(scenarios.add(fixture.scenario()), "fixture scenario unique");
            check(json.contains("\"sampleBasisPoints\""), "camelCase fixture");
            check(!json.contains("rawQuery"), "fixture omits raw query");
            check(fixture.sampleBasisPoints() >= 0 && fixture.sampleBasisPoints() <= 10_000, "fixture sampling range");
            check(fixture.mode().matches("[a-z][a-z0-9_]*"), "fixture mode wire");
            check(fixture.expectedStatus().matches("[a-z][a-z0-9_]*"), "fixture status wire");
        }
        List<Class<? extends Enum<?>>> enums = List.of(SearchShadowWiringMode.class,
                SearchShadowDispatchStatus.class, SearchShadowExecutorStatus.class,
                SearchShadowCircuitState.class, SearchShadowLogStatus.class);
        for (Class<? extends Enum<?>> type : enums) for (Enum<?> value : type.getEnumConstants()) {
            check(value instanceof WireValue, "enum implements WireValue");
            check(((WireValue) value).wireValue().matches("[a-z][a-z0-9_]*"), "lowercase_snake_case enum");
        }
        check(SearchShadowWiringContractIds.WIRING.equals(new ContractId("search-shadow-wiring-v1")), "wiring contract ID");
        check(SearchShadowWiringContractIds.STRUCTURED_RECORD.equals(new ContractId("search-shadow-structured-record-v1")), "record contract ID");
        expectFailure(() -> SearchShadowWiringFixtureJsonCodecV1.read("{}"));
    }

    private static void configurationAndActivationContracts() {
        SearchShadowActivationGate gate = new SearchShadowActivationGate();
        var all = new SearchShadowActivationInputsV1(true, true, true);
        var disabled = SearchShadowWiringConfigV1.disabledByDefault(BUILD);
        check(disabled.mode() == SearchShadowWiringMode.DISABLED, "default mode disabled");
        check(disabled.sampleBasisPoints() == 0, "default sample zero");
        check(!disabled.explicitAllow(), "default explicit allow false");
        check(!gate.decide(disabled, all).activated(), "disabled gate not active");
        check(SearchShadowWiringMode.parseOrDisabled(null) == SearchShadowWiringMode.DISABLED, "missing mode disabled");
        check(SearchShadowWiringMode.parseOrDisabled("") == SearchShadowWiringMode.DISABLED, "blank mode disabled");
        check(SearchShadowWiringMode.parseOrDisabled("unknown") == SearchShadowWiringMode.DISABLED, "unknown mode disabled");
        check(SearchShadowWiringMode.parseOrDisabled("test_only") == SearchShadowWiringMode.TEST_ONLY, "test mode parsed");
        var valid = config(SearchShadowWiringMode.TEST_ONLY, true, 10_000);
        check(gate.decide(valid, all).activated(), "explicit test-only profile activates test harness");
        check(!gate.decide(config(SearchShadowWiringMode.TEST_ONLY, false, 10_000), all).activated(), "missing allow blocked");
        var wrongProfile = new SearchShadowWiringConfigV1(SearchShadowWiringMode.TEST_ONLY, "prod", true, 10_000,
                Duration.ofMillis(100), 1, 1, new PolicyVersion("search-shadow-sampling-policy-v1"), BUILD);
        check(!gate.decide(wrongProfile, all).activated(), "production profile blocked");
        check(!gate.decide(config(SearchShadowWiringMode.SHADOW_CANDIDATE, true, 10_000), all).activated(), "shadow candidate is hold state");
        check(!gate.decide(valid, new SearchShadowActivationInputsV1(false,true,true)).activated(), "missing input provider blocked");
        check(!gate.decide(valid, new SearchShadowActivationInputsV1(true,false,true)).activated(), "missing executor blocked");
        check(!gate.decide(valid, new SearchShadowActivationInputsV1(true,true,false)).activated(), "missing logger blocked");
        expectFailure(() -> new SearchShadowWiringConfigV1(SearchShadowWiringMode.TEST_ONLY, "search-shadow-test", true, -1,
                Duration.ofMillis(1),1,1,new PolicyVersion("search-shadow-sampling-policy-v1"),BUILD));
        expectFailure(() -> new SearchShadowWiringConfigV1(SearchShadowWiringMode.TEST_ONLY, "search-shadow-test", true, 10001,
                Duration.ofMillis(1),1,1,new PolicyVersion("search-shadow-sampling-policy-v1"),BUILD));
        expectFailure(() -> new SearchShadowWiringConfigV1(SearchShadowWiringMode.TEST_ONLY, "search-shadow-test", true, 1,
                Duration.ZERO,1,1,new PolicyVersion("search-shadow-sampling-policy-v1"),BUILD));
        expectFailure(() -> new SearchShadowWiringConfigV1(SearchShadowWiringMode.TEST_ONLY, "search-shadow-test", true, 1,
                Duration.ofMillis(1),0,1,new PolicyVersion("search-shadow-sampling-policy-v1"),BUILD));
    }

    private static void deterministicSamplingContracts() {
        DeterministicSearchShadowSampler sampler = new DeterministicSearchShadowSampler();
        SearchShadowSamplingPolicyV1 zero = new SearchShadowSamplingPolicyV1(0, new PolicyVersion("search-shadow-sampling-policy-v1"));
        SearchShadowSamplingPolicyV1 all = new SearchShadowSamplingPolicyV1(10_000, new PolicyVersion("search-shadow-sampling-policy-v1"));
        for (int i=0;i<200;i++) {
            String id="correlation:"+i;
            var a=sampler.decide(id,zero); var b=sampler.decide(id,zero);
            check(!a.included(), "zero sampling excludes");
            check(a.equals(b), "sampling deterministic");
            check(sampler.decide(id,all).included(), "full test sampling includes");
            check(a.bucket()>=0 && a.bucket()<10000, "bucket range");
            check(a.decisionFingerprint().matches("[0-9a-f]{64}"), "sampling fingerprint");
        }
        var p1=sampler.decide("correlation:stable",new SearchShadowSamplingPolicyV1(5000,new PolicyVersion("search-shadow-sampling-policy-v1")));
        var p2=sampler.decide("correlation:stable",new SearchShadowSamplingPolicyV1(5000,new PolicyVersion("search-shadow-sampling-policy-v1")));
        check(p1.equals(p2), "partial sampling repeatable");
        expectFailure(() -> sampler.decide(" ", zero));
        expectFailure(() -> new SearchShadowSamplingPolicyV1(-1,new PolicyVersion("search-shadow-sampling-policy-v1")));
    }

    private static void executorCircuitAndBackpressureContracts() {
        var closed=new FixedSearchShadowCircuitBreaker(SearchShadowCircuitState.CLOSED,false).evaluate(hash("c"));
        var open=new FixedSearchShadowCircuitBreaker(SearchShadowCircuitState.OPEN,false).evaluate(hash("c"));
        var halfYes=new FixedSearchShadowCircuitBreaker(SearchShadowCircuitState.HALF_OPEN,true).evaluate(hash("c"));
        var halfNo=new FixedSearchShadowCircuitBreaker(SearchShadowCircuitState.HALF_OPEN,false).evaluate(hash("c"));
        check(closed.permitted(), "closed circuit permits");
        check(!open.permitted(), "open circuit blocks shadow only");
        check(halfYes.permitted(), "half-open explicit fixture trial");
        check(!halfNo.permitted(), "half-open fixture may block");
        for (SearchShadowExecutorStatus status : SearchShadowExecutorStatus.values()) {
            ControlledExecutor executor=new ControlledExecutor(status);
            var result=executor.submit(() -> "ok",Duration.ofMillis(10));
            check(result.status()==status, "executor status preserved "+status);
            check(status==SearchShadowExecutorStatus.COMPLETED ? "ok".equals(result.value()) : result.value()==null,
                    "executor value contract "+status);
        }
        expectFailure(() -> SearchShadowExecutorResultV1.failure(SearchShadowExecutorStatus.COMPLETED,"bad"));
    }

    private static void disabledNoOpAndAdapterContracts() {
        List<String> response=new ArrayList<>(List.of("post:1","post:2"));
        var hookRequest=hookRequest(response);
        var noOp=new NoOpSearchShadowHook<List<String>>().dispatch(hookRequest);
        check(noOp.status()==SearchShadowDispatchStatus.DISABLED, "no-op disabled");
        check(noOp.legacyResponse()==response, "no-op exact response identity");
        check(noOp.integrationResult()==null, "no-op no comparison");
        ControlledExecutor executor=new ControlledExecutor(SearchShadowExecutorStatus.COMPLETED);
        var dispatcher=dispatcher(SearchShadowWiringConfigV1.disabledByDefault(BUILD),executor,
                new FixedSearchShadowCircuitBreaker(SearchShadowCircuitState.CLOSED,false),
                successPort(),new InMemoryLogPort(false));
        var adapter=new BackendExploreShadowHookAdapter<List<String>>(new LegacyExploreCompatibilityAdapter(),dispatcher);
        var receipt=adapter.dispatch(hookRequest);
        check(receipt.status()==SearchShadowDispatchStatus.DISABLED, "adapter default disabled");
        check(executor.submissions()==0, "disabled causes zero executor submissions");
        check(receipt.legacyResponse()==response, "disabled adapter response identity");
    }

    private static void controlledDispatchAndResponseAuthorityContracts() {
        List<String> response=new ArrayList<>(List.of("post:1","post:2"));
        ControlledExecutor executor=new ControlledExecutor(SearchShadowExecutorStatus.COMPLETED);
        InMemoryLogPort logger=new InMemoryLogPort(false);
        var dispatcher=dispatcher(config(SearchShadowWiringMode.TEST_ONLY,true,10_000),executor,
                new FixedSearchShadowCircuitBreaker(SearchShadowCircuitState.CLOSED,false),successPort(),logger);
        LegacyExploreCompatibilityResult legacy=legacy();
        var first=dispatcher.dispatch(response,legacy,shadowContext());
        check(first.status()==SearchShadowDispatchStatus.COMPLETED, "controlled comparison completed");
        check(first.legacyResponse()==response, "response identity preserved");
        check(response.equals(List.of("post:1","post:2")), "response deep equality preserved");
        check(executor.submissions()==1, "one bounded submission");
        check(logger.records().size()==1, "one memory-only structured record");
        check(first.integrationResult().authority().equals(SearchShadowAuthorityV1.legacyOnly()), "IP-6 legacy authority retained");
        check(first.authority().equals(SearchShadowWiringAuthorityV1.legacyOnly()), "wiring authority none");
        check("legacy".equals(first.authority().responseAuthority()), "legacy response authority explicit");
        check(!first.authority().responseModified(), "responseModified false");
        check(!first.authority().persistenceAuthority() && !first.authority().exposureAuthority(), "no persistence/exposure authority");
        check(!first.authority().releaseGateAuthority() && !first.authority().productionCursorAuthority(), "no release/cursor authority");
        check(!first.authority().productionActivationAuthority() && !first.authority().apiCutoverAuthority(), "no activation/cutover authority");

        ControlledExecutor reject=new ControlledExecutor(SearchShadowExecutorStatus.REJECTED);
        var rejected=dispatcher(config(SearchShadowWiringMode.TEST_ONLY,true,10_000),reject,
                new FixedSearchShadowCircuitBreaker(SearchShadowCircuitState.CLOSED,false),successPort(),logger)
                .dispatch(response,legacy,shadowContext());
        check(rejected.status()==SearchShadowDispatchStatus.REJECTED, "rejection isolated");
        check(rejected.legacyResponse()==response, "rejection response identity");
        for (SearchShadowExecutorStatus status : List.of(SearchShadowExecutorStatus.QUEUE_FULL,
                SearchShadowExecutorStatus.EXECUTOR_UNAVAILABLE,SearchShadowExecutorStatus.TIMED_OUT,SearchShadowExecutorStatus.FAILED)) {
            var r=dispatcher(config(SearchShadowWiringMode.TEST_ONLY,true,10_000),new ControlledExecutor(status),
                    new FixedSearchShadowCircuitBreaker(SearchShadowCircuitState.CLOSED,false),successPort(),logger)
                    .dispatch(response,legacy,shadowContext());
            check(r.legacyResponse()==response, "backpressure response unchanged "+status);
            check(r.integrationResult()==null, "backpressure no integration result "+status);
        }
        var circuit=dispatcher(config(SearchShadowWiringMode.TEST_ONLY,true,10_000),executor,
                new FixedSearchShadowCircuitBreaker(SearchShadowCircuitState.OPEN,false),successPort(),logger)
                .dispatch(response,legacy,shadowContext());
        check(circuit.status()==SearchShadowDispatchStatus.CIRCUIT_OPEN, "circuit open skips shadow");
        check(circuit.legacyResponse()==response, "circuit response unchanged");
        var unsampled=dispatcher(config(SearchShadowWiringMode.TEST_ONLY,true,0),executor,
                new FixedSearchShadowCircuitBreaker(SearchShadowCircuitState.CLOSED,false),successPort(),logger)
                .dispatch(response,legacy,shadowContext());
        check(unsampled.status()==SearchShadowDispatchStatus.NOT_SAMPLED, "zero sampling skips");
    }

    private static void actualIp6BoundaryAndPagedResponseContracts() {
        PagedLegacyResponse response = new PagedLegacyResponse(List.of("post:1", "post:2"), 0, 20, 2L, 1, true);
        var ip6 = new SearchShadowIntegrationBoundary<PagedLegacyResponse>(
                new SearchShadowPolicyV1(SearchShadowMode.TEST_ONLY, new PolicyVersion("search-shadow-policy-v1"),
                        new PolicyVersion("search-shadow-comparison-policy-v1"), Duration.ofMillis(100), 10, BUILD),
                execution -> { throw new AssertionError("runtime must not execute when input is unavailable"); },
                new DirectTestSearchShadowExecutionPort(Duration.ofMillis(1)));
        InMemoryLogPort logger = new InMemoryLogPort(false);
        var receipt = new DefaultSearchShadowDispatcher<>(config(SearchShadowWiringMode.TEST_ONLY, true, 10_000),
                new ControlledExecutor(SearchShadowExecutorStatus.COMPLETED),
                new FixedSearchShadowCircuitBreaker(SearchShadowCircuitState.CLOSED, false), ip6,
                new UnavailableSearchShadowRuntimeInputProvider("runtime_input_unavailable"), logger)
                .dispatch(response, legacy(), shadowContext());
        check(receipt.status() == SearchShadowDispatchStatus.INPUT_UNAVAILABLE, "actual IP-6 input-unavailable status propagated");
        check(receipt.legacyResponse() == response, "actual IP-6 preserves paged response identity");
        check(receipt.legacyResponse().equals(new PagedLegacyResponse(List.of("post:1", "post:2"), 0, 20, 2L, 1, true)),
                "paged response deep equality preserved");
        check(receipt.legacyResponse().items().equals(List.of("post:1", "post:2")), "paged item order preserved");
        check(receipt.legacyResponse().page() == 0 && receipt.legacyResponse().size() == 20,
                "pagination values preserved");
        check(receipt.legacyResponse().totalElements() == 2L && receipt.legacyResponse().last(),
                "pagination metadata preserved");
        ControlledExecutor undersized = new ControlledExecutor(SearchShadowExecutorStatus.COMPLETED, 1, 1);
        var blocked = dispatcher(config(SearchShadowWiringMode.TEST_ONLY, true, 10_000), undersized,
                new FixedSearchShadowCircuitBreaker(SearchShadowCircuitState.CLOSED, false), successPort(), logger)
                .dispatch(new ArrayList<>(List.of("post:1")), legacy(), shadowContext());
        check(blocked.status() == SearchShadowDispatchStatus.DISABLED, "undersized bounded executor does not activate");
        check(undersized.submissions() == 0, "undersized executor receives no task");
    }

    private static void loggingPrivacyAndFailureIsolationContracts() {
        List<String> response=new ArrayList<>(List.of("post:1","post:2"));
        InMemoryLogPort logger=new InMemoryLogPort(false);
        var receipt=dispatcher(config(SearchShadowWiringMode.TEST_ONLY,true,10_000),new ControlledExecutor(SearchShadowExecutorStatus.COMPLETED),
                new FixedSearchShadowCircuitBreaker(SearchShadowCircuitState.CLOSED,false),successPort(),logger)
                .dispatch(response,legacy(),shadowContext());
        SearchShadowStructuredRecordV1 record=logger.records().get(0);
        check(record.correlationFingerprint().matches("[0-9a-f]{64}"), "correlation is fingerprinted");
        check(!record.toString().contains("correlation:ip7"), "raw correlation absent");
        check(!record.toString().contains("서울 여행"), "raw query absent");
        check(record.authority().equals(SearchShadowWiringAuthorityV1.legacyOnly()), "log authority legacy-only");
        check(record.warningCodes().equals(List.of(SearchShadowWarningCode.NON_PERSISTENT_EVIDENCE)), "warning ordering stable");
        check(Double.isFinite(record.topKOverlapRatio()), "log metric finite");
        Set<String> names=new HashSet<>();
        for(RecordComponent c:SearchShadowStructuredRecordV1.class.getRecordComponents()) names.add(c.getName());
        check(!names.contains("rawQuery") && !names.contains("normalizedQuery"), "record schema omits query text");
        check(!names.contains("sessionId") && !names.contains("correlationId"), "record schema omits raw IDs");
        InMemoryLogPort failing=new InMemoryLogPort(true);
        var failed=dispatcher(config(SearchShadowWiringMode.TEST_ONLY,true,10_000),new ControlledExecutor(SearchShadowExecutorStatus.COMPLETED),
                new FixedSearchShadowCircuitBreaker(SearchShadowCircuitState.CLOSED,false),successPort(),failing)
                .dispatch(response,legacy(),shadowContext());
        check(failed.status()==SearchShadowDispatchStatus.LOGGING_FAILED, "logging failure isolated");
        check(failed.legacyResponse()==response, "logging failure response unchanged");
        var noOp=new NoOpSearchShadowComparisonLogPort().log(record);
        check(noOp.status()==SearchShadowLogStatus.SKIPPED, "default logger no-op");
        check(receipt.logResult().status()==SearchShadowLogStatus.ACCEPTED, "test logger accepts in memory");
    }

    private static void determinismBoundaryAndNullSafetyContracts() {
        List<String> response=new ArrayList<>(List.of("post:1","post:2"));
        for(int i=0;i<100;i++) {
            InMemoryLogPort logger=new InMemoryLogPort(false);
            var dispatcher=dispatcher(config(SearchShadowWiringMode.TEST_ONLY,true,10_000),new ControlledExecutor(SearchShadowExecutorStatus.COMPLETED),
                    new FixedSearchShadowCircuitBreaker(SearchShadowCircuitState.CLOSED,false),successPort(),logger);
            var a=dispatcher.dispatch(response,legacy(),shadowContext());
            var b=dispatcher.dispatch(response,legacy(),shadowContext());
            check(a.status()==b.status(), "receipt status deterministic");
            check(a.samplingDecision().equals(b.samplingDecision()), "sampling receipt deterministic");
            check(logger.records().get(0).equals(logger.records().get(1)), "structured record deterministic");
            check(response.equals(List.of("post:1","post:2")), "repeated dispatch no mutation");
        }
        expectFailure(() -> new SearchShadowWiringAuthorityV1("shadow",false,false,false,false,false,false,false,false));
        expectFailure(() -> new SearchShadowDispatchReceiptV1<>(response,SearchShadowDispatchStatus.COMPLETED,null,null,null,null,"Bad Code",SearchShadowWiringAuthorityV1.legacyOnly()));
        expectFailure(() -> new SearchShadowHookRequestV1<>(null,null,null,null,null));
        var brokenIdentityPort=(SearchShadowIntegrationPort<List<String>>) (legacyResponse,legacyCompatibility,context,provider) ->
                new SearchShadowIntegrationResult<>(List.copyOf(legacyResponse),SearchShadowStatus.COMPARED,evidence(),SearchShadowAuthorityV1.legacyOnly());
        var broken=dispatcher(config(SearchShadowWiringMode.TEST_ONLY,true,10_000),new ControlledExecutor(SearchShadowExecutorStatus.COMPLETED),
                new FixedSearchShadowCircuitBreaker(SearchShadowCircuitState.CLOSED,false),brokenIdentityPort,new InMemoryLogPort(false))
                .dispatch(response,legacy(),shadowContext());
        check(broken.status()==SearchShadowDispatchStatus.FAILED, "response identity violation contained");
        check(broken.legacyResponse()==response, "identity violation returns original legacy response");
    }

    private static void architectureAndForbiddenDependencyContracts() throws IOException {
        Path project=findProjectRoot();
        Path main=project.resolve("jc-search-shadow-wiring/src/main/java");
        List<Path> sources;
        try(var stream=Files.walk(main)){sources=stream.filter(p->p.toString().endsWith(".java")).toList();}
        check(sources.size()>=30, "wiring module has explicit contracts");
        String all="";
        StringBuilder builder=new StringBuilder();
        for(Path p:sources) builder.append(Files.readString(p)).append('\n');
        all=builder.toString();
        for(String forbidden:List.of("com.jc.backend","com.jc.recommendation","org.springframework","jakarta.persistence",
                "java.sql","EntityManager","Repository","@Component","@Service","@Configuration","ForkJoinPool.commonPool",
                "new Thread(","Executors.newCachedThreadPool","Kafka")) {
            check(!all.contains(forbidden), "forbidden dependency absent: "+forbidden);
        }
        check(!all.contains("rawQuery"), "production wiring source never stores rawQuery");
        check(!all.contains("search_exposure_v1"), "no exposure writer activation");
        check(!all.contains("INSERT ") && !all.contains("UPDATE ") && !all.contains("DELETE "), "no persistence statements");
        String settings=Files.readString(project.resolve("jc-backend/settings.gradle.kts"));
        check(settings.contains(":jc-search-shadow-wiring"), "module registered");
        String build=Files.readString(project.resolve("jc-search-shadow-wiring/build.gradle.kts"));
        check(build.contains("searchShadowWiringContractTest"), "dedicated Gradle task");
        check(!build.contains("jc-backend") && !build.contains("jc-recommendation-core"), "no backend/recommendation dependency");
    }

    private static SearchShadowWiringConfigV1 config(SearchShadowWiringMode mode, boolean allow, int bps) {
        return new SearchShadowWiringConfigV1(mode,SearchShadowWiringConfigV1.TEST_PROFILE,allow,bps,Duration.ofMillis(100),4,2,
                new PolicyVersion("search-shadow-sampling-policy-v1"),BUILD);
    }
    private static DefaultSearchShadowDispatcher<List<String>> dispatcher(SearchShadowWiringConfigV1 config,
            SearchShadowExecutor executor, FixedSearchShadowCircuitBreaker circuit,
            SearchShadowIntegrationPort<List<String>> port, SearchShadowComparisonLogPort logger) {
        SearchShadowRuntimeInputProvider provider=context -> { throw new AssertionError("fake integration port must not consume compatibility output as runtime source"); };
        return new DefaultSearchShadowDispatcher<>(config,executor,circuit,port,provider,logger);
    }
    private static SearchShadowIntegrationPort<List<String>> successPort() {
        return (legacyResponse,legacyCompatibility,context,provider) -> new SearchShadowIntegrationResult<>(legacyResponse,
                SearchShadowStatus.COMPARED,evidence(),SearchShadowAuthorityV1.legacyOnly());
    }
    private static SearchShadowComparisonEvidenceV1 evidence() {
        return new SearchShadowComparisonEvidenceV1(SearchIntegrationContractIds.COMPARISON_EVIDENCE,"comparison:ip7",
                "correlation:ip7",SearchShadowMode.TEST_ONLY,new PolicyVersion("search-shadow-policy-v1"),
                "legacy-explore-v1",hash("request"),hash("response"),hash("input"),hash("result"),
                new PolicyVersion("search-shadow-comparison-policy-v1"),TIME,SearchShadowStatus.COMPARED,
                SearchShadowRuntimeInputStatus.AVAILABLE,"success",SearchShadowComparisonStatus.COMPARED,
                new SearchShadowComparisonMetricsV1(2,2,2,0,0,2,1.0d,2,0,Duration.ofMillis(1),Duration.ofMillis(2)),
                List.of(),List.of(SearchShadowWarningCode.NON_PERSISTENT_EVIDENCE),SearchShadowSeverity.INFO,BUILD,
                SearchShadowAuthorityV1.legacyOnly());
    }
    private static LegacyExploreCompatibilityResult legacy() {
        return new LegacyExploreCompatibilityAdapter().adapt(legacyRequest(),legacyPage(),new LegacyExploreCompatibilityContext("request:ip7", "correlation:ip7", "session:opaque", TIME, TIME, BUILD));
    }
    private static LegacyExploreRequestView legacyRequest() {
        return new LegacyExploreRequestView("서울 여행","seoul",0,20,List.of(),java.util.Map.of());
    }
    private static LegacyExplorePageView legacyPage() {
        var author=new LegacyExploreAuthorView(10L,"tester",null);
        return new LegacyExplorePageView(List.of(
                new LegacyExploreItemView(1L,"one","KR-11","서울",null,10L,2L,1L,author,TIME.minusSeconds(2)),
                new LegacyExploreItemView(2L,"two","KR-11","서울",null,9L,1L,0L,author,TIME.minusSeconds(1))),
                0,20,2L,1,true);
    }
    private static SearchShadowContextV1 shadowContext() { return new SearchShadowContextV1("request:ip7","correlation:ip7","session:opaque",TIME); }
    private static SearchShadowHookRequestV1<List<String>> hookRequest(List<String> response) {
        return new SearchShadowHookRequestV1<>(response,legacyRequest(),legacyPage(),new LegacyExploreCompatibilityContext("request:ip7", "correlation:ip7", "session:opaque", TIME, TIME, BUILD),shadowContext());
    }
    private static String hash(String value){return SearchShadowFingerprintV1.sha256(value);}
    private static List<Path> fixturePaths() throws IOException {
        Path dir=findProjectRoot().resolve("jc-search-shadow-wiring/src/test/resources/search-shadow-wiring");
        try(var stream=Files.list(dir)){return stream.filter(p->p.toString().endsWith(".json")).sorted().toList();}
    }
    private static Path findProjectRoot() {
        Path p=Path.of("").toAbsolutePath();
        while(p!=null && !Files.isDirectory(p.resolve("jc-search-shadow-wiring"))) p=p.getParent();
        if(p==null) throw new IllegalStateException("project root not found");
        return p;
    }
    private static void check(boolean condition,String message){assertions++;if(!condition)throw new AssertionError(message);}
    private static void expectFailure(Runnable action){assertions++;try{action.run();throw new AssertionError("expected failure");}catch(IllegalArgumentException expected){}}

    private record PagedLegacyResponse(List<String> items, int page, int size, long totalElements, int totalPages, boolean last) {
        private PagedLegacyResponse { items = List.copyOf(items); }
    }

    private static final class ControlledExecutor implements SearchShadowExecutor {
        private final SearchShadowExecutorStatus status; private final int capacity; private final int concurrency; private int submissions;
        ControlledExecutor(SearchShadowExecutorStatus status){this(status,4,2);}
        ControlledExecutor(SearchShadowExecutorStatus status,int capacity,int concurrency){this.status=status;this.capacity=capacity;this.concurrency=concurrency;}
        @Override public boolean available(){return status!=SearchShadowExecutorStatus.EXECUTOR_UNAVAILABLE;}
        @Override public int queueCapacity(){return capacity;}
        @Override public int maxConcurrency(){return concurrency;}
        @Override public <T> SearchShadowExecutorResultV1<T> submit(com.jc.intelligence.wiring.search.v1.SearchShadowTask<T> task,Duration timeout){
            submissions++; if(status==SearchShadowExecutorStatus.COMPLETED)return SearchShadowExecutorResultV1.completed(task.execute());
            return SearchShadowExecutorResultV1.failure(status,status.wireValue());
        }
        int submissions(){return submissions;}
    }
    private static final class InMemoryLogPort implements SearchShadowComparisonLogPort {
        private final boolean fail; private final List<SearchShadowStructuredRecordV1> records=new ArrayList<>();
        InMemoryLogPort(boolean fail){this.fail=fail;}
        @Override public boolean available(){return true;}
        @Override public SearchShadowComparisonLogResultV1 log(SearchShadowStructuredRecordV1 record){
            if(fail)return new SearchShadowComparisonLogResultV1(SearchShadowLogStatus.FAILED,"logging_failed");
            records.add(record); return new SearchShadowComparisonLogResultV1(SearchShadowLogStatus.ACCEPTED,"memory_only");
        }
        List<SearchShadowStructuredRecordV1> records(){return List.copyOf(records);}
    }
}
