package com.jc.intelligence.integration.search;

import com.jc.intelligence.compat.search.explore.v1.LegacyExploreAuthorView;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreCompatibilityAdapter;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreCompatibilityContext;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreCompatibilityEvidence;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreCompatibilityExplanation;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreCompatibilityPolicy;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreCompatibilityResult;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreCompatibilityStatus;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreContractIds;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreExplanationCode;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreItemView;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreMappedItem;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreMappedRequest;
import com.jc.intelligence.compat.search.explore.v1.LegacyExplorePageMetadata;
import com.jc.intelligence.compat.search.explore.v1.LegacyExplorePageView;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreRequestView;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreSortDirection;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreSortOrderView;
import com.jc.intelligence.contract.support.WireValue;
import com.jc.intelligence.contract.v1.identity.EntityRef;
import com.jc.intelligence.contract.v1.identity.RunRef;
import com.jc.intelligence.contract.v1.identity.SnapshotRef;
import com.jc.intelligence.contract.v1.search.RetrievalSource;
import com.jc.intelligence.contract.v1.search.SearchContractIds;
import com.jc.intelligence.contract.v1.search.SearchEligibilityState;
import com.jc.intelligence.contract.v1.search.SearchEntityScope;
import com.jc.intelligence.contract.v1.search.SearchEntityType;
import com.jc.intelligence.contract.v1.search.SearchQueryMode;
import com.jc.intelligence.contract.v1.search.SearchSortType;
import com.jc.intelligence.contract.v1.search.SearchSurface;
import com.jc.intelligence.contract.v1.search.SearchVisibilityState;
import com.jc.intelligence.contract.v1.search.query.SearchContextV1;
import com.jc.intelligence.contract.v1.search.query.SearchPageRequestV1;
import com.jc.intelligence.contract.v1.search.query.SearchQueryCanonicalizerV1;
import com.jc.intelligence.contract.v1.search.query.SearchRequestV1;
import com.jc.intelligence.contract.v1.search.query.SearchSortV1;
import com.jc.intelligence.contract.v1.search.retrieval.RetrievalCandidateV1;
import com.jc.intelligence.contract.v1.version.FeatureDefinitionVersion;
import com.jc.intelligence.contract.v1.version.PolicyVersion;
import com.jc.intelligence.contract.v1.version.ProducerBuildId;
import com.jc.intelligence.contract.v1.version.SchemaVersion;
import com.jc.intelligence.integration.search.v1.SearchShadowActivationDecisionV1;
import com.jc.intelligence.integration.search.v1.SearchIntegrationContractIds;
import com.jc.intelligence.integration.search.v1.SearchShadowAuthorityV1;
import com.jc.intelligence.integration.search.v1.SearchShadowComparisonHarness;
import com.jc.intelligence.integration.search.v1.SearchShadowComparisonStatus;
import com.jc.intelligence.integration.search.v1.SearchShadowContextV1;
import com.jc.intelligence.integration.search.v1.SearchShadowExecutionOutcomeV1;
import com.jc.intelligence.integration.search.v1.SearchShadowFingerprintV1;
import com.jc.intelligence.integration.search.v1.SearchShadowIntegrationBoundary;
import com.jc.intelligence.integration.search.v1.SearchShadowMismatchCode;
import com.jc.intelligence.integration.search.v1.SearchShadowMode;
import com.jc.intelligence.integration.search.v1.SearchShadowPolicyV1;
import com.jc.intelligence.integration.search.v1.SearchShadowResponseGuard;
import com.jc.intelligence.integration.search.v1.SearchShadowRuntimeInputResultV1;
import com.jc.intelligence.integration.search.v1.SearchShadowRuntimeInputStatus;
import com.jc.intelligence.integration.search.v1.SearchShadowSeverity;
import com.jc.intelligence.integration.search.v1.SearchShadowStatus;
import com.jc.intelligence.integration.search.v1.UnavailableSearchShadowRuntimeInputProvider;
import com.jc.intelligence.integration.search.v1.fixture.DirectTestSearchShadowExecutionPort;
import com.jc.intelligence.integration.search.v1.fixture.FixtureSearchShadowRuntimeInputProvider;
import com.jc.intelligence.integration.search.v1.fixture.SearchShadowFixtureCaseV1;
import com.jc.intelligence.integration.search.v1.fixture.SearchShadowFixtureJsonCodecV1;
import com.jc.intelligence.runtime.search.v1.DefaultSearchRuntime;
import com.jc.intelligence.runtime.search.v1.SearchRuntime;
import com.jc.intelligence.runtime.search.v1.SearchRuntimeExecutionRequestV1;
import com.jc.intelligence.runtime.search.v1.SearchRuntimeStatus;
import com.jc.intelligence.runtime.search.v1.fixture.DeterministicFixtureSearchRankingPort;
import com.jc.intelligence.runtime.search.v1.fixture.InMemorySearchRetrievalPort;
import com.jc.intelligence.runtime.search.v1.fixture.PassThroughSearchCandidateFilter;
import com.jc.intelligence.runtime.search.v1.port.SearchDependencyDecision;
import com.jc.intelligence.runtime.search.v1.port.SearchRetrievalResultV1;
import com.jc.intelligence.runtime.search.v1.ranking.NoOpSearchRerankingPort;
import com.jc.intelligence.runtime.search.v1.ranking.SearchRankingResultV1;
import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SearchIntegrationContractTest {
    private static final Instant REFERENCE_TIME = Instant.parse("2026-07-19T12:00:00Z");
    private static final Instant COMPLETED_AT = REFERENCE_TIME.plusMillis(10);
    private static final ProducerBuildId BUILD = new ProducerBuildId("ip6-test-build");
    private static int assertions;

    private SearchIntegrationContractTest() { }

    public static void main(String[] args) throws Exception {
        fixtureRoundTripAndWireContracts();
        disabledAndActivationContracts();
        exactAndMismatchComparisonContracts();
        failOpenInputFailureAndTimeoutContracts();
        comparisonFailureAndNullSafetyContracts();
        responseAuthorityAndPrivacyContracts();
        deterministicAndBoundaryContracts();
        architectureAndSourceIsolationContracts();
        System.out.println("IP-6 Search integration boundary assertions: " + assertions + " PASS");
    }

    private static void fixtureRoundTripAndWireContracts() throws IOException {
        List<Path> paths = fixturePaths();
        check(paths.size() == 12, "12 integration JSON fixtures exist");
        Set<String> scenarios = new HashSet<>();
        for (Path path : paths) {
            String json = Files.readString(path);
            SearchShadowFixtureCaseV1 fixture = SearchShadowFixtureJsonCodecV1.read(json);
            SearchShadowFixtureCaseV1 roundTrip = SearchShadowFixtureJsonCodecV1.read(
                    SearchShadowFixtureJsonCodecV1.write(fixture));
            check(fixture.equals(roundTrip), "fixture round trip " + path.getFileName());
            check(scenarios.add(fixture.scenario()), "fixture scenario unique");
            check(json.contains("\"runtimeInputStatus\""), "camelCase fixture fields");
            check(!json.contains("rawQuery"), "fixtures omit raw query");
            check(fixture.topK() >= 1 && fixture.topK() <= 100, "fixture topK boundary");
            check(fixture.legacyEntityRefs().stream().allMatch(value -> value.contains(":")), "legacy typed refs");
            check(fixture.runtimeEntityRefs().stream().allMatch(value -> value.contains(":")), "runtime typed refs");
        }
        List<Class<? extends Enum<?>>> enums = List.of(
                SearchShadowMode.class, SearchShadowStatus.class, SearchShadowRuntimeInputStatus.class,
                SearchShadowMismatchCode.class, SearchShadowSeverity.class);
        for (Class<? extends Enum<?>> type : enums) {
            for (Enum<?> value : type.getEnumConstants()) {
                check(value instanceof WireValue, "enum implements WireValue");
                String wire = ((WireValue) value).wireValue();
                check(wire.matches("[a-z][a-z0-9_]*"), "wire lowercase_snake_case: " + wire);
                check(!wire.equals(value.name()) || value.name().equals(value.name().toLowerCase()), "ordinal/name not serialized");
            }
        }
        expectFailure(() -> SearchShadowFixtureJsonCodecV1.read("{}"));
        expectFailure(() -> new SearchShadowFixtureCaseV1("bad", "test_only", "available", "success", 0,
                false, List.of(), List.of()));
    }

    private static void disabledAndActivationContracts() {
        SearchShadowFixtureCaseV1 fixture = fixture("exact-match.json");
        Object legacyResponse = List.copyOf(fixture.legacyEntityRefs());
        LegacyExploreCompatibilityResult legacy = legacy(fixture.legacyEntityRefs());
        AtomicBoolean providerCalled = new AtomicBoolean(false);
        SearchShadowPolicyV1 disabled = SearchShadowPolicyV1.disabledByDefault(BUILD);
        var boundary = boundary(disabled, fixture, new DirectTestSearchShadowExecutionPort(Duration.ofMillis(1)),
                new SearchShadowComparisonHarness());
        var result = boundary.integrate(legacyResponse, legacy, context(), inputContext -> {
            providerCalled.set(true);
            return SearchShadowRuntimeInputResultV1.unavailable("not configured");
        });
        check(result.shadowStatus() == SearchShadowStatus.DISABLED, "shadow disabled by default");
        check(!providerCalled.get(), "disabled mode does not invoke provider");
        check(result.legacyResponse() == legacyResponse, "disabled result preserves exact response object");
        check(result.comparisonEvidence() == null, "disabled mode does not fabricate evidence");
        check(result.authority().equals(SearchShadowAuthorityV1.legacyOnly()), "disabled authority legacy only");
        check(SearchShadowMode.fromWireOrDisabled(null) == SearchShadowMode.DISABLED, "missing mode disabled");
        check(SearchShadowMode.fromWireOrDisabled("unknown") == SearchShadowMode.DISABLED, "unknown mode disabled");
        check(new UnavailableSearchShadowRuntimeInputProvider("runtime source not registered")
                .provide(new com.jc.intelligence.integration.search.v1.SearchShadowRuntimeInputContextV1(
                        "correlation:ip6", REFERENCE_TIME,
                        SearchShadowFingerprintV1.sha256("request"), SearchShadowFingerprintV1.sha256("response")))
                .status() == SearchShadowRuntimeInputStatus.UNAVAILABLE,
                "default unavailable provider boundary");
        check(SearchShadowMode.fromWireOrDisabled("test_only") == SearchShadowMode.TEST_ONLY, "known mode parsed");
        var disabledDecision = SearchShadowActivationDecisionV1.decide(disabled);
        check(!disabledDecision.activated() && !disabledDecision.testOnly(), "disabled activation decision");
        var testDecision = SearchShadowActivationDecisionV1.decide(policy(SearchShadowMode.TEST_ONLY, 10));
        check(testDecision.activated() && testDecision.testOnly(), "test-only activation decision");
        var shadowDecision = SearchShadowActivationDecisionV1.decide(policy(SearchShadowMode.SHADOW_ENABLED, 10));
        check(shadowDecision.activated() && !shadowDecision.testOnly(), "shadow-enabled activation decision");
        expectFailure(() -> new SearchShadowPolicyV1(SearchShadowMode.TEST_ONLY,
                new PolicyVersion("search-shadow-policy-v1"), new PolicyVersion("search-shadow-comparison-policy-v1"),
                Duration.ZERO, 10, BUILD));
        expectFailure(() -> new SearchShadowAuthorityV1("runtime", false, false, false, false, false, false, false));
        expectFailure(() -> new SearchShadowAuthorityV1("legacy", true, false, false, false, false, false, false));
    }

    private static void exactAndMismatchComparisonContracts() {
        var exact = runFixture("exact-match.json");
        check(exact.shadowStatus() == SearchShadowStatus.COMPARED, "exact fixture compared");
        check(has(exact, SearchShadowMismatchCode.LEGACY_SUCCESS_RUNTIME_SUCCESS), "status pair recorded");
        check(!has(exact, SearchShadowMismatchCode.COUNT_MISMATCH), "exact count match");
        check(!has(exact, SearchShadowMismatchCode.ENTITY_SET_MISMATCH), "exact entity set match");
        check(!has(exact, SearchShadowMismatchCode.ORDERING_MISMATCH), "exact order match");
        check(exact.comparisonEvidence().metrics().topKOverlapRatio() == 1.0d, "exact top-K overlap 1");
        check(exact.comparisonEvidence().metrics().sameOrderPrefixLength() == 3, "exact same prefix");
        assertNotComparableDimensions(exact);

        var ordering = runFixture("ordering-mismatch.json");
        check(has(ordering, SearchShadowMismatchCode.ORDERING_MISMATCH), "ordering mismatch classified");
        check(has(ordering, SearchShadowMismatchCode.POSITION_MISMATCH), "position mismatch classified");
        check(ordering.comparisonEvidence().metrics().intersectionCount() == 3, "order mismatch same set");
        check(ordering.comparisonEvidence().metrics().sameOrderPrefixLength() == 0, "order prefix differs immediately");

        var count = runFixture("count-mismatch.json");
        check(has(count, SearchShadowMismatchCode.COUNT_MISMATCH), "count mismatch classified");
        check(has(count, SearchShadowMismatchCode.ENTITY_SET_MISMATCH), "set mismatch classified");
        check(has(count, SearchShadowMismatchCode.MISSING_ENTITY), "missing legacy entity classified");
        check(count.comparisonEvidence().metrics().legacyOnlyCount() == 1, "legacy-only count");

        var runtimeOnly = runFixture("runtime-only.json");
        check(has(runtimeOnly, SearchShadowMismatchCode.UNEXPECTED_ENTITY), "runtime-only entity classified");
        check(runtimeOnly.comparisonEvidence().metrics().runtimeOnlyCount() == 1, "runtime-only count");

        var legacyOnly = runFixture("legacy-only.json");
        check(has(legacyOnly, SearchShadowMismatchCode.MISSING_ENTITY), "legacy-only entity classified");
        check(legacyOnly.comparisonEvidence().metrics().legacyOnlyCount() == 1, "legacy-only metric");

        var empty = runFixture("empty-both.json");
        check(empty.comparisonEvidence().metrics().legacyCount() == 0, "empty legacy count");
        check(empty.comparisonEvidence().metrics().runtimeCount() == 0, "empty runtime count");
        check(empty.comparisonEvidence().metrics().topKOverlapRatio() == 1.0d, "zero denominator overlap rule is one");
        check(empty.comparisonEvidence().metrics().sameOrderPrefixLength() == 0, "empty prefix zero");

        var fallback = runFixture("runtime-fallback.json");
        check(fallback.shadowStatus() == SearchShadowStatus.COMPARED, "fallback result remains comparable");
        check("fallback".equals(fallback.comparisonEvidence().runtimeStatus()), "fallback status retained");
        check(fallback.legacyResponse() instanceof List<?>, "fallback does not replace legacy type");

        var mixed = runFixture("mixed-entity-types.json");
        check(mixed.shadowStatus() == SearchShadowStatus.COMPARED, "mixed entity types compared");
        check(has(mixed, SearchShadowMismatchCode.ORDERING_MISMATCH), "mixed entity order mismatch");
        check(mixed.comparisonEvidence().metrics().intersectionCount() == 3, "mixed entity set intersection");

        var emptyLegacyRuntimeOnly = integrate(List.of(), List.of("post:1"), "success", 3,
                SearchShadowMode.TEST_ONLY, SearchShadowRuntimeInputStatus.AVAILABLE, false,
                new SearchShadowComparisonHarness());
        check(has(emptyLegacyRuntimeOnly, SearchShadowMismatchCode.UNEXPECTED_ENTITY),
                "legacy empty/runtime non-empty classified");
        check(emptyLegacyRuntimeOnly.comparisonEvidence().metrics().runtimeOnlyCount() == 1,
                "legacy empty runtime-only metric");

        var duplicateRuntime = integrate(List.of("post:1"), List.of("post:1", "post:1"), "success", 3,
                SearchShadowMode.TEST_ONLY, SearchShadowRuntimeInputStatus.AVAILABLE, false,
                new SearchShadowComparisonHarness());
        check(duplicateRuntime.shadowStatus() == SearchShadowStatus.NOT_COMPARABLE,
                "duplicate runtime candidate cannot become successful snapshot");
        check(has(duplicateRuntime, SearchShadowMismatchCode.RUNTIME_FAILURE),
                "duplicate runtime candidate surfaces typed runtime failure");

        SearchShadowFixtureCaseV1 exactFixture = fixture("exact-match.json");
        Object unsupportedResponse = List.copyOf(exactFixture.legacyEntityRefs());
        var unsupportedSort = boundary(policy(SearchShadowMode.TEST_ONLY, 3), exactFixture,
                new DirectTestSearchShadowExecutionPort(Duration.ofMillis(1)), new SearchShadowComparisonHarness())
                .integrate(unsupportedResponse, unsupportedLegacySort(), context(), availableProvider(exactFixture));
        check(has(unsupportedSort, SearchShadowMismatchCode.LEGACY_FAILURE_RUNTIME_SUCCESS),
                "legacy failure/runtime success classified");
        check(has(unsupportedSort, SearchShadowMismatchCode.UNSUPPORTED_LEGACY_SORT),
                "unsupported legacy sort not-comparable classification");
    }

    private static void failOpenInputFailureAndTimeoutContracts() {
        var unavailable = runFixture("input-unavailable.json");
        check(unavailable.shadowStatus() == SearchShadowStatus.INPUT_UNAVAILABLE, "input unavailable status");
        check(has(unavailable, SearchShadowMismatchCode.RUNTIME_INPUT_UNAVAILABLE), "input unavailable mismatch");
        check(unavailable.comparisonEvidence().metrics().legacyCount() == 1, "input unavailable retains legacy count");
        check(unavailable.legacyResponse() instanceof List<?>, "input unavailable fail-open response");
        check(unavailable.comparisonEvidence().runtimeInputFingerprint() == null, "unavailable has no fake input fingerprint");

        var unsupported = runFixture("input-unsupported.json");
        check(unsupported.shadowStatus() == SearchShadowStatus.INPUT_UNSUPPORTED, "input unsupported status");
        check(has(unsupported, SearchShadowMismatchCode.RUNTIME_INPUT_UNSUPPORTED), "unsupported mismatch");

        var timeout = runFixture("timeout.json");
        check(timeout.shadowStatus() == SearchShadowStatus.TIMED_OUT, "runtime timeout isolated");
        check(has(timeout, SearchShadowMismatchCode.RUNTIME_TIMEOUT), "timeout mismatch");
        check(timeout.comparisonEvidence().metrics().runtimeDuration().compareTo(Duration.ofMillis(100)) > 0,
                "timeout duration preserved");
        check(timeout.authority().responseAuthority().equals("legacy"), "timeout retains legacy authority");

        var runtimeFailed = runFixture("runtime-failed.json");
        check(runtimeFailed.shadowStatus() == SearchShadowStatus.NOT_COMPARABLE, "completed failed runtime not comparable");
        check(has(runtimeFailed, SearchShadowMismatchCode.LEGACY_SUCCESS_RUNTIME_FAILED), "runtime failure status pair");
        check(has(runtimeFailed, SearchShadowMismatchCode.RUNTIME_FAILURE), "runtime failure mismatch");
        check(runtimeFailed.legacyResponse() instanceof List<?>, "runtime failure fail-open");

        SearchShadowFixtureCaseV1 fixture = fixture("exact-match.json");
        LegacyExploreCompatibilityResult legacy = legacy(fixture.legacyEntityRefs());
        Object response = List.copyOf(fixture.legacyEntityRefs());
        var throwingProvider = boundary(policy(SearchShadowMode.TEST_ONLY, fixture.topK()), fixture,
                new DirectTestSearchShadowExecutionPort(Duration.ofMillis(1)), new SearchShadowComparisonHarness())
                .integrate(response, legacy, context(), ignored -> { throw new IllegalStateException("private provider details"); });
        check(throwingProvider.shadowStatus() == SearchShadowStatus.INVALID_INPUT, "provider exception contained");
        check(has(throwingProvider, SearchShadowMismatchCode.RUNTIME_INPUT_INVALID), "provider exception typed invalid input");
        check(throwingProvider.legacyResponse() == response, "provider exception fail-open exact object");
        check(!throwingProvider.comparisonEvidence().toString().contains("private provider details"), "provider exception hidden");

        var throwingExecution = boundary(policy(SearchShadowMode.TEST_ONLY, fixture.topK()), fixture,
                (runtime, request, deadline) -> { throw new IllegalStateException("executor details"); },
                new SearchShadowComparisonHarness()).integrate(response, legacy, context(), availableProvider(fixture));
        check(throwingExecution.shadowStatus() == SearchShadowStatus.RUNTIME_FAILED, "execution exception contained");
        check(throwingExecution.legacyResponse() == response, "execution exception fail-open");
        check(!throwingExecution.comparisonEvidence().toString().contains("executor details"), "executor exception hidden");
    }

    private static void comparisonFailureAndNullSafetyContracts() {
        SearchShadowFixtureCaseV1 fixture = fixture("exact-match.json");
        LegacyExploreCompatibilityResult legacy = legacy(fixture.legacyEntityRefs());
        Object response = List.copyOf(fixture.legacyEntityRefs());
        var comparisonFailure = boundary(policy(SearchShadowMode.TEST_ONLY, fixture.topK()), fixture,
                new DirectTestSearchShadowExecutionPort(Duration.ofMillis(1)),
                (left, right, topK, duration) -> { throw new IllegalStateException("comparison internals"); })
                .integrate(response, legacy, context(), availableProvider(fixture));
        check(comparisonFailure.shadowStatus() == SearchShadowStatus.COMPARISON_FAILED, "comparison failure contained");
        check(has(comparisonFailure, SearchShadowMismatchCode.COMPARISON_FAILURE), "comparison failure classified");
        check(comparisonFailure.comparisonEvidence().comparisonStatus() == SearchShadowComparisonStatus.FAILED,
                "comparison failed evidence status");
        check(!comparisonFailure.comparisonEvidence().toString().contains("comparison internals"), "comparison raw failure hidden");

        var nullExecution = boundary(policy(SearchShadowMode.TEST_ONLY, fixture.topK()), fixture,
                (runtime, request, deadline) -> null, new SearchShadowComparisonHarness())
                .integrate(response, legacy, context(), availableProvider(fixture));
        check(nullExecution.shadowStatus() == SearchShadowStatus.RUNTIME_FAILED, "null execution result contained");
        check(nullExecution.legacyResponse() == response, "null execution fail-open");

        var nullProvider = boundary(policy(SearchShadowMode.TEST_ONLY, fixture.topK()), fixture,
                new DirectTestSearchShadowExecutionPort(Duration.ofMillis(1)), new SearchShadowComparisonHarness())
                .integrate(response, legacy, context(), ignored -> null);
        check(nullProvider.shadowStatus() == SearchShadowStatus.INVALID_INPUT, "null provider result contained");

        expectFailure(() -> boundary(policy(SearchShadowMode.TEST_ONLY, fixture.topK()), fixture,
                new DirectTestSearchShadowExecutionPort(Duration.ofMillis(1)), new SearchShadowComparisonHarness())
                .integrate(null, legacy, context(), availableProvider(fixture)));
        expectFailure(() -> new SearchShadowResponseGuard().preserve(new Object(), new Object()));
        expectFailure(() -> SearchShadowRuntimeInputResultV1.available(execution(fixture), "bad"));
        expectFailure(() -> SearchShadowRuntimeInputResultV1.unavailable(" "));
    }

    private static void responseAuthorityAndPrivacyContracts() {
        var result = runFixture("exact-match.json");
        var authority = result.authority();
        check(authority.responseAuthority().equals("legacy"), "legacy response authority true");
        check(!authority.responseModified(), "response modified false");
        check(!authority.persistenceAuthority(), "persistence authority false");
        check(!authority.exposureAuthority(), "exposure authority false");
        check(!authority.releaseGateAuthority(), "release gate authority false");
        check(!authority.metricAuthority(), "metric authority false");
        check(!authority.productionCursorAuthority(), "production cursor authority false");
        check(!authority.apiCutoverAuthority(), "API cutover authority false");
        check(result.comparisonEvidence().authority().equals(authority), "evidence authority same");
        check(result.comparisonEvidence().warningCodes().stream().anyMatch(code -> code.wireValue().equals("raw_query_omitted")),
                "raw query omission warning");
        check(result.comparisonEvidence().warningCodes().stream().anyMatch(code -> code.wireValue().equals("non_persistent_evidence")),
                "non-persistent evidence warning");
        String evidence = result.comparisonEvidence().toString();
        check(!evidence.contains("서울 여행"), "evidence omits raw query");
        check(!evidence.toLowerCase().contains("token"), "evidence omits token");
        check(!evidence.contains("candidateMetadata"), "evidence omits candidate payload");
        for (RecordComponent component : result.comparisonEvidence().getClass().getRecordComponents()) {
            String name = component.getName().toLowerCase();
            check(!name.contains("rawquery"), "no raw query evidence field");
            check(!name.contains("payload"), "no raw payload evidence field");
            check(!name.contains("token"), "no token evidence field");
            check(!name.contains("location"), "no precise location evidence field");
        }
        check(result.comparisonEvidence().contractVersion().equals(SearchIntegrationContractIds.COMPARISON_EVIDENCE),
                "comparison evidence contract version");
        check(result.comparisonEvidence().comparisonId().startsWith("comparison:"), "derived comparison ID");
        check(result.comparisonEvidence().legacyEndpointId().equals("get-api-v1-explore"), "legacy endpoint ID preserved");
        check(result.comparisonEvidence().legacyRequestFingerprint().matches("[0-9a-f]{64}"), "legacy request fingerprint");
        check(result.comparisonEvidence().legacyResponseFingerprint().matches("[0-9a-f]{64}"), "legacy response fingerprint");
        check(result.comparisonEvidence().runtimeInputFingerprint().matches("[0-9a-f]{64}"), "runtime input fingerprint");
        check(result.comparisonEvidence().runtimeResultFingerprint().matches("[0-9a-f]{64}"), "runtime result fingerprint");

        SearchShadowFixtureCaseV1 fixture = fixture("exact-match.json");
        LegacyEnvelope envelope = new LegacyEnvelope(List.copyOf(fixture.legacyEntityRefs()), 2, 20, 63L);
        var envelopeResult = new SearchShadowIntegrationBoundary<LegacyEnvelope>(
                policy(SearchShadowMode.TEST_ONLY, 3), runtime(fixture),
                new DirectTestSearchShadowExecutionPort(Duration.ofMillis(1)))
                .integrate(envelope, legacy(fixture.legacyEntityRefs()), context(), availableProvider(fixture));
        check(envelopeResult.legacyResponse() == envelope, "legacy envelope identity preserved");
        check(envelopeResult.legacyResponse().items().equals(fixture.legacyEntityRefs()), "legacy envelope order preserved");
        check(envelopeResult.legacyResponse().page() == 2 && envelopeResult.legacyResponse().size() == 20,
                "legacy pagination metadata preserved");
        check(envelopeResult.legacyResponse().total() == 63L, "legacy total preserved");
    }

    private static void deterministicAndBoundaryContracts() {
        SearchShadowFixtureCaseV1 fixture = fixture("ordering-mismatch.json");
        var first = runFixture("ordering-mismatch.json");
        for (int i = 0; i < 50; i++) {
            var repeated = runFixture("ordering-mismatch.json");
            check(repeated.comparisonEvidence().comparisonId().equals(first.comparisonEvidence().comparisonId()),
                    "deterministic comparison ID iteration " + i);
            check(repeated.comparisonEvidence().mismatches().equals(first.comparisonEvidence().mismatches()),
                    "deterministic mismatch ordering iteration " + i);
            check(repeated.comparisonEvidence().metrics().equals(first.comparisonEvidence().metrics()),
                    "deterministic metrics iteration " + i);
            check(repeated.legacyResponse().equals(first.legacyResponse()), "deterministic pass-through iteration " + i);
            check(repeated.comparisonEvidence().warningCodes().equals(first.comparisonEvidence().warningCodes()),
                    "deterministic warning ordering iteration " + i);
        }
        List<SearchShadowMismatchCode> codes = first.comparisonEvidence().mismatches().stream()
                .map(item -> item.code()).toList();
        List<SearchShadowMismatchCode> sorted = codes.stream().sorted(Comparator.comparingInt(Enum::ordinal)).toList();
        check(codes.equals(sorted), "mismatch codes ordered deterministically");
        check(new LinkedHashSet<>(first.comparisonEvidence().mismatches()).size()
                == first.comparisonEvidence().mismatches().size(), "mismatches unique");

        List<String> sameTopKLegacy = List.of("post:1", "post:2", "post:3", "post:4");
        List<String> sameTopKRuntime = List.of("post:1", "post:2", "post:4", "post:3");
        var sameTopK = integrate(sameTopKLegacy, sameTopKRuntime, "success", 2, SearchShadowMode.TEST_ONLY,
                SearchShadowRuntimeInputStatus.AVAILABLE, false, new SearchShadowComparisonHarness());
        check(sameTopK.comparisonEvidence().metrics().topKOverlapCount() == 2, "same top-K overlap count");
        check(sameTopK.comparisonEvidence().metrics().topKOverlapRatio() == 1.0d, "same top-K overlap ratio");
        check(has(sameTopK, SearchShadowMismatchCode.ORDERING_MISMATCH), "different tail ordering mismatch");

        var zeroOverlap = integrate(List.of("post:1", "post:2"), List.of("post:3", "post:4"), "success", 2,
                SearchShadowMode.TEST_ONLY, SearchShadowRuntimeInputStatus.AVAILABLE, false,
                new SearchShadowComparisonHarness());
        check(zeroOverlap.comparisonEvidence().metrics().topKOverlapCount() == 0, "zero top-K overlap count");
        check(zeroOverlap.comparisonEvidence().metrics().topKOverlapRatio() == 0.0d, "zero top-K overlap ratio");

        List<String> max = new ArrayList<>();
        for (int i = 1; i <= 100; i++) max.add("post:" + i);
        var maximum = integrate(max, max, "success", 100, SearchShadowMode.TEST_ONLY,
                SearchShadowRuntimeInputStatus.AVAILABLE, false, new SearchShadowComparisonHarness());
        check(maximum.comparisonEvidence().metrics().legacyCount() == 100, "maximum legacy count");
        check(maximum.comparisonEvidence().metrics().runtimeCount() == 100, "maximum runtime count");
        check(maximum.comparisonEvidence().metrics().topKOverlapCount() == 100, "maximum overlap count");

        LegacyExploreCompatibilityResult duplicateLegacy = legacyManual(List.of("post:1", "post:1", "post:2"));
        SearchShadowFixtureCaseV1 base = fixture("exact-match.json");
        Object response = List.of("post:1", "post:1", "post:2");
        var duplicate = boundary(policy(SearchShadowMode.TEST_ONLY, 3), base,
                new DirectTestSearchShadowExecutionPort(Duration.ofMillis(1)), new SearchShadowComparisonHarness())
                .integrate(response, duplicateLegacy, context(), availableProvider(
                        new SearchShadowFixtureCaseV1("duplicate", "test_only", "available", "success", 3, false,
                                List.of("post:1", "post:1", "post:2"), List.of("post:1", "post:2"))));
        check(has(duplicate, SearchShadowMismatchCode.DUPLICATE_ENTITY), "duplicate legacy entity classified");
        check(duplicate.comparisonEvidence().metrics().duplicateCount() == 1, "duplicate metric stable");

        String directA = SearchShadowFingerprintV1.sha256("stable");
        String directB = SearchShadowFingerprintV1.sha256("stable");
        check(directA.equals(directB), "SHA-256 deterministic");
        check(!directA.equals(SearchShadowFingerprintV1.sha256("different")), "SHA-256 input sensitive");
        check(REFERENCE_TIME.equals(first.comparisonEvidence().referenceTime()), "supplied reference time preserved");
        check(fixture.runtimeEntityRefs().equals(List.of("post:2", "post:1", "post:3")), "fixture input unchanged");
    }

    private static void architectureAndSourceIsolationContracts() throws IOException {
        Path root = projectRoot();
        Path module = root.resolve("jc-search-integration");
        List<Path> javaFiles;
        try (var stream = Files.walk(module.resolve("src/main/java"))) {
            javaFiles = stream.filter(path -> path.toString().endsWith(".java")).toList();
        }
        check(javaFiles.size() >= 30, "integration module has independent contract surface");
        for (Path path : javaFiles) {
            String source = Files.readString(path);
            check(!source.contains("org.springframework"), "no Spring dependency " + path.getFileName());
            check(!source.contains("jakarta.persistence"), "no JPA dependency " + path.getFileName());
            check(!source.contains("java.sql"), "no JDBC dependency " + path.getFileName());
            check(!source.contains("com.jc.backend"), "no backend dependency " + path.getFileName());
            check(!source.contains("com.jc.recommendation"), "no recommendation dependency " + path.getFileName());
            check(!source.contains("@Component") && !source.contains("@Service") && !source.contains("@Configuration"),
                    "no component auto-activation " + path.getFileName());
            check(!source.contains("ForkJoinPool") && !source.contains("new Thread"), "no unmanaged production thread " + path.getFileName());
            check(!source.contains("EntityManager") && !source.contains("Repository"), "no repository access " + path.getFileName());
        }
        String build = Files.readString(module.resolve("build.gradle.kts"));
        check(build.contains("api(project(\":jc-search-runtime\"))"), "runtime dependency explicit");
        check(build.contains("api(project(\":jc-search-compatibility\"))"), "compatibility dependency explicit");
        check(!build.contains("jc-backend") && !build.contains("jc-recommendation-core"), "forbidden module dependencies absent");
        String settings = Files.readString(root.resolve("jc-backend/settings.gradle.kts"));
        check(settings.contains("include(\":jc-search-integration\")"), "module registered minimally");

        List<String> protectedPaths = List.of(
                "jc-backend/src/main/java/com/jc/backend/post/PostController.java",
                "jc-backend/src/main/java/com/jc/backend/post/PostService.java",
                "jc-backend/src/main/java/com/jc/backend/post/JourneyPostRepository.java",
                "jc-backend/src/main/java/com/jc/backend/post/PostDtos.java",
                "jc-backend/src/main/kotlin/com/jc/backend/config/SecurityConfig.kt");
        for (String relative : protectedPaths) check(Files.isRegularFile(root.resolve(relative)), "protected path exists " + relative);
        check(!Files.exists(root.resolve("jc-search-integration/src/main/resources/application.yml")), "no production activation config");
        check(!Files.exists(root.resolve("jc-search-integration/src/main/resources")), "no runtime resources/persistence config");
    }

    private static void assertNotComparableDimensions(
            com.jc.intelligence.integration.search.v1.SearchShadowIntegrationResult<?> result) {
        check(has(result, SearchShadowMismatchCode.PAGINATION_NOT_COMPARABLE), "pagination not comparable");
        check(has(result, SearchShadowMismatchCode.CURSOR_NOT_COMPARABLE), "cursor not comparable");
        check(has(result, SearchShadowMismatchCode.VISIBILITY_NOT_COMPARABLE), "visibility not comparable");
        check(has(result, SearchShadowMismatchCode.RANKING_NOT_COMPARABLE), "ranking not comparable");
        check(result.comparisonEvidence().mismatches().stream()
                .filter(item -> Set.of(SearchShadowMismatchCode.PAGINATION_NOT_COMPARABLE,
                        SearchShadowMismatchCode.CURSOR_NOT_COMPARABLE,
                        SearchShadowMismatchCode.VISIBILITY_NOT_COMPARABLE,
                        SearchShadowMismatchCode.RANKING_NOT_COMPARABLE).contains(item.code()))
                .allMatch(item -> item.severity() == SearchShadowSeverity.NOT_COMPARABLE),
                "not-comparable dimensions use not_comparable severity");
    }

    private static com.jc.intelligence.integration.search.v1.SearchShadowIntegrationResult<Object> runFixture(String name) {
        SearchShadowFixtureCaseV1 fixture = fixture(name);
        SearchShadowMode mode = fixture.mode().equals("unknown") ? SearchShadowMode.fromWireOrDisabled("unknown")
                : SearchShadowMode.fromWireOrDisabled(fixture.mode());
        return integrate(fixture.legacyEntityRefs(), fixture.runtimeEntityRefs(), fixture.runtimeStatus(), fixture.topK(),
                mode, SearchShadowRuntimeInputStatus.valueOf(fixture.runtimeInputStatus().toUpperCase(java.util.Locale.ROOT)),
                fixture.timeout(), new SearchShadowComparisonHarness());
    }

    private static com.jc.intelligence.integration.search.v1.SearchShadowIntegrationResult<Object> integrate(
            List<String> legacyRefs,
            List<String> runtimeRefs,
            String runtimeStatus,
            int topK,
            SearchShadowMode mode,
            SearchShadowRuntimeInputStatus inputStatus,
            boolean timeout,
            com.jc.intelligence.integration.search.v1.SearchShadowComparator comparator) {
        SearchShadowFixtureCaseV1 fixture = new SearchShadowFixtureCaseV1("inline", mode.wireValue(),
                inputStatus.wireValue(), runtimeStatus, topK, timeout, legacyRefs, runtimeRefs);
        Object legacyResponse = List.copyOf(legacyRefs);
        LegacyExploreCompatibilityResult legacy = legacy(legacyRefs);
        Duration duration = timeout ? Duration.ofMillis(200) : Duration.ofMillis(1);
        var boundary = boundary(policy(mode, topK), fixture, new DirectTestSearchShadowExecutionPort(duration), comparator);
        return boundary.integrate(legacyResponse, legacy, context(), provider(fixture, inputStatus));
    }

    private static SearchShadowIntegrationBoundary<Object> boundary(
            SearchShadowPolicyV1 policy,
            SearchShadowFixtureCaseV1 fixture,
            com.jc.intelligence.integration.search.v1.SearchShadowExecutionPort executionPort,
            com.jc.intelligence.integration.search.v1.SearchShadowComparator comparator) {
        SearchRuntime runtime = runtime(fixture);
        return new SearchShadowIntegrationBoundary<>(policy, runtime, executionPort, comparator, new SearchShadowResponseGuard());
    }

    private static SearchShadowPolicyV1 policy(SearchShadowMode mode, int topK) {
        return new SearchShadowPolicyV1(mode, new PolicyVersion("search-shadow-policy-v1"),
                new PolicyVersion("search-shadow-comparison-policy-v1"), Duration.ofMillis(100), topK, BUILD);
    }

    private static SearchShadowRuntimeInputProviderAdapter availableProvider(SearchShadowFixtureCaseV1 fixture) {
        return new SearchShadowRuntimeInputProviderAdapter(new FixtureSearchShadowRuntimeInputProvider(
                SearchShadowRuntimeInputResultV1.available(execution(fixture), inputFingerprint(fixture))));
    }

    private static com.jc.intelligence.integration.search.v1.SearchShadowRuntimeInputProvider provider(
            SearchShadowFixtureCaseV1 fixture, SearchShadowRuntimeInputStatus status) {
        return switch (status) {
            case AVAILABLE -> availableProvider(fixture);
            case UNAVAILABLE -> new FixtureSearchShadowRuntimeInputProvider(
                    SearchShadowRuntimeInputResultV1.unavailable("runtime input source unavailable"));
            case UNSUPPORTED -> new FixtureSearchShadowRuntimeInputProvider(
                    SearchShadowRuntimeInputResultV1.unsupported("legacy request shape unsupported"));
            case INVALID -> new FixtureSearchShadowRuntimeInputProvider(
                    SearchShadowRuntimeInputResultV1.invalid("runtime input invalid"));
        };
    }

    private static String inputFingerprint(SearchShadowFixtureCaseV1 fixture) {
        return SearchShadowFingerprintV1.runtimeInput(execution(fixture));
    }

    private static SearchRuntime runtime(SearchShadowFixtureCaseV1 fixture) {
        List<RetrievalCandidateV1> candidates = candidates(fixture.runtimeEntityRefs());
        SearchRetrievalResultV1 retrieval = fixture.runtimeStatus().equals("failed")
                ? SearchRetrievalResultV1.failed("fixture_runtime_failure")
                : SearchRetrievalResultV1.success(candidates);
        LinkedHashMap<EntityRef, Double> scores = new LinkedHashMap<>();
        LinkedHashMap<EntityRef, String> keys = new LinkedHashMap<>();
        for (int index = 0; index < candidates.size(); index++) {
            scores.put(candidates.get(index).entityRef(), Double.valueOf(1000.0d - index));
            keys.put(candidates.get(index).entityRef(), String.format(java.util.Locale.ROOT, "%04d", index));
        }
        var ranking = fixture.runtimeStatus().equals("fallback")
                ? (com.jc.intelligence.runtime.search.v1.ranking.SearchRankingPort)
                        request -> SearchRankingResultV1.failed("fixture_primary_ranking_failure")
                : new DeterministicFixtureSearchRankingPort(scores, keys);
        return new DefaultSearchRuntime(new InMemorySearchRetrievalPort(retrieval),
                new PassThroughSearchCandidateFilter(),
                (request, candidate) -> SearchDependencyDecision.ALLOW,
                (request, candidate) -> SearchDependencyDecision.ALLOW,
                ranking,
                new NoOpSearchRerankingPort());
    }

    private static SearchRuntimeExecutionRequestV1 execution(SearchShadowFixtureCaseV1 fixture) {
        List<RetrievalCandidateV1> candidates = candidates(fixture.runtimeEntityRefs());
        List<RetrievalSource> sources = candidates.stream().map(RetrievalCandidateV1::retrievalSource).distinct().toList();
        if (sources.isEmpty()) sources = List.of(RetrievalSource.DATABASE_POST);
        return new SearchRuntimeExecutionRequestV1(request(fixture.runtimeEntityRefs()), new RunRef("run:ip6-shadow"),
                new SchemaVersion("search-runtime-foundation-v1"), new SchemaVersion("search-retrieval-fixture-v1"),
                sources, new PolicyVersion("search-fallback-foundation-v1"), BUILD,
                REFERENCE_TIME, COMPLETED_AT, 1000, true);
    }

    private static SearchRequestV1 request(List<String> refs) {
        var query = SearchQueryCanonicalizerV1.canonicalize(SearchQueryMode.TEXT_QUERY, "  서울   여행  ", "ko", "ko-KR");
        SearchEntityScope scope = refs.stream().map(value -> value.substring(0, value.indexOf(':'))).distinct().count() > 1
                ? SearchEntityScope.ALL : refs.isEmpty() ? SearchEntityScope.POST
                        : SearchEntityScope.fromWire(refs.get(0).substring(0, refs.get(0).indexOf(':')));
        PolicyVersion rankingPolicy = new PolicyVersion("search-ranking-foundation-v1");
        return new SearchRequestV1(SearchContractIds.SEARCH_DOMAIN, "request:ip6", "correlation:ip6", query,
                new SearchContextV1(null, "session:ip6", SearchSurface.GLOBAL_SEARCH, scope, REFERENCE_TIME,
                        "ko", "ko-KR", null, null), List.of(), new SearchSortV1(SearchSortType.RELEVANCE, rankingPolicy),
                SearchPageRequestV1.firstPage(100), new SchemaVersion("search-request-v1"),
                query.normalizationVersion(), rankingPolicy, new FeatureDefinitionVersion("search-feature-foundation-v1"));
    }

    private static List<RetrievalCandidateV1> candidates(List<String> refs) {
        ArrayList<RetrievalCandidateV1> result = new ArrayList<>();
        for (int index = 0; index < refs.size(); index++) {
            EntityRef ref = new EntityRef(refs.get(index));
            SearchEntityType type = SearchEntityType.fromWire(ref.entityType());
            result.add(new RetrievalCandidateV1(SearchContractIds.SEARCH_RETRIEVAL_RANKING, ref, type, ref.sourceId(),
                    retrievalSource(type), null, Integer.valueOf(index + 1), REFERENCE_TIME,
                    new SnapshotRef("snapshot:ip6-fixture-source"), SearchEligibilityState.UNKNOWN,
                    SearchVisibilityState.UNKNOWN, null, new SchemaVersion("search-retrieval-fixture-v1")));
        }
        return List.copyOf(result);
    }

    private static RetrievalSource retrievalSource(SearchEntityType type) {
        return switch (type) {
            case POST -> RetrievalSource.DATABASE_POST;
            case REGION -> RetrievalSource.DATABASE_REGION;
            case TAG -> RetrievalSource.DATABASE_TAG;
            case PLACE -> RetrievalSource.DATABASE_PLACE;
            case USER -> RetrievalSource.DATABASE_USER;
            case CREW -> RetrievalSource.DATABASE_CREW;
        };
    }

    private static LegacyExploreCompatibilityResult legacy(List<String> refs) {
        boolean allPosts = refs.stream().allMatch(value -> value.startsWith("post:"));
        boolean unique = new LinkedHashSet<>(refs).size() == refs.size();
        if (!allPosts || !unique) return legacyManual(refs);
        ArrayList<LegacyExploreItemView> items = new ArrayList<>();
        for (String ref : refs) {
            long id = Long.parseLong(ref.substring(ref.indexOf(':') + 1));
            items.add(new LegacyExploreItemView(id, "Post " + id, "KR-11", "Seoul", null,
                    Long.valueOf(10), Long.valueOf(2), Long.valueOf(1),
                    new LegacyExploreAuthorView(Long.valueOf(1), "author", null), REFERENCE_TIME));
        }
        LegacyExploreRequestView request = new LegacyExploreRequestView("서울 여행", null, Integer.valueOf(0),
                Integer.valueOf(Math.max(20, refs.size())), List.of(), Map.of());
        LegacyExplorePageView page = new LegacyExplorePageView(items, Integer.valueOf(0),
                Integer.valueOf(Math.max(20, refs.size())), Long.valueOf(refs.size()),
                Integer.valueOf(refs.isEmpty() ? 0 : 1), Boolean.TRUE);
        LegacyExploreCompatibilityResult result = new LegacyExploreCompatibilityAdapter().adapt(request, page,
                new LegacyExploreCompatibilityContext("request:ip6", "correlation:ip6", "session:ip6",
                        REFERENCE_TIME, REFERENCE_TIME, BUILD));
        if (result.status() != LegacyExploreCompatibilityStatus.SUCCESS) {
            throw new IllegalStateException("fixture legacy adapter failed: " + result.failure());
        }
        return result;
    }

    private static LegacyExploreCompatibilityResult legacyManual(List<String> refs) {
        var query = SearchQueryCanonicalizerV1.canonicalize(SearchQueryMode.TEXT_QUERY, "서울 여행", "ko", "ko-KR");
        SearchEntityScope scope = refs.stream().map(value -> value.substring(0, value.indexOf(':'))).distinct().count() > 1
                ? SearchEntityScope.ALL : refs.isEmpty() ? SearchEntityScope.POST
                        : SearchEntityScope.fromWire(refs.get(0).substring(0, refs.get(0).indexOf(':')));
        LegacyExploreMappedRequest request = new LegacyExploreMappedRequest(query,
                new SearchContextV1(null, "session:ip6", SearchSurface.EXPLORE, scope, REFERENCE_TIME,
                        "ko", "ko-KR", null, null), List.of(),
                new SearchSortV1(SearchSortType.RECENT, LegacyExploreContractIds.ORDER_POLICY),
                0, Math.max(20, refs.size()), false, false);
        ArrayList<LegacyExploreMappedItem> items = new ArrayList<>();
        for (int index = 0; index < refs.size(); index++) {
            EntityRef ref = new EntityRef(refs.get(index));
            SearchEntityType type = SearchEntityType.fromWire(ref.entityType());
            LegacyExploreItemView item = new LegacyExploreItemView(Long.valueOf(index + 1), "Legacy " + index,
                    null, null, null, Long.valueOf(0), Long.valueOf(0), Long.valueOf(0),
                    new LegacyExploreAuthorView(Long.valueOf(1), "author", null), REFERENCE_TIME);
            items.add(new LegacyExploreMappedItem(ref, type, ref.sourceId(), index + 1, null, null, null, null,
                    SearchEligibilityState.UNKNOWN, SearchVisibilityState.UNKNOWN, item,
                    new LegacyExploreCompatibilityExplanation(
                            List.of(LegacyExploreExplanationCode.LEGACY_PUBLISHED_AT_DESC_ID_DESC_ORDER), false, false),
                    LegacyExploreCompatibilityPolicy.BASE_WARNINGS));
        }
        String requestHash = SearchShadowFingerprintV1.sha256("manual-legacy-request\n" + String.join("\n", refs));
        String responseHash = SearchShadowFingerprintV1.sha256("manual-legacy-response\n" + String.join("\n", refs));
        LegacyExploreCompatibilityEvidence evidence = new LegacyExploreCompatibilityEvidence(
                LegacyExploreContractIds.ADAPTER, LegacyExploreContractIds.ENDPOINT_ID, requestHash, responseHash,
                LegacyExploreContractIds.MAPPING_POLICY, REFERENCE_TIME, BUILD, refs.size(), refs.size(), 0,
                LegacyExploreCompatibilityPolicy.BASE_WARNINGS, false, false, false, false);
        return new LegacyExploreCompatibilityResult(LegacyExploreCompatibilityStatus.SUCCESS, request, items,
                new LegacyExplorePageMetadata(0, Math.max(20, refs.size()), 0L, refs.size(), refs.isEmpty() ? 0 : 1,
                        true, false), evidence, null, false, false, false);
    }

    private static LegacyExploreCompatibilityResult unsupportedLegacySort() {
        LegacyExploreRequestView request = new LegacyExploreRequestView("서울 여행", null, Integer.valueOf(0),
                Integer.valueOf(20), List.of(new LegacyExploreSortOrderView("title", LegacyExploreSortDirection.ASC)),
                Map.of());
        LegacyExplorePageView page = new LegacyExplorePageView(List.of(), Integer.valueOf(0), Integer.valueOf(20),
                Long.valueOf(0), Integer.valueOf(0), Boolean.TRUE);
        LegacyExploreCompatibilityResult result = new LegacyExploreCompatibilityAdapter().adapt(request, page,
                new LegacyExploreCompatibilityContext("request:ip6", "correlation:ip6", "session:ip6",
                        REFERENCE_TIME, REFERENCE_TIME, BUILD));
        if (result.status() == LegacyExploreCompatibilityStatus.SUCCESS) {
            throw new IllegalStateException("unsupported legacy sort fixture unexpectedly succeeded");
        }
        return result;
    }

    private static SearchShadowContextV1 context() {
        return new SearchShadowContextV1("request:ip6", "correlation:ip6", "session:ip6", REFERENCE_TIME);
    }

    private static boolean has(
            com.jc.intelligence.integration.search.v1.SearchShadowIntegrationResult<?> result,
            SearchShadowMismatchCode code) {
        return result.comparisonEvidence().mismatches().stream().anyMatch(item -> item.code() == code);
    }

    private static SearchShadowFixtureCaseV1 fixture(String name) {
        try { return SearchShadowFixtureJsonCodecV1.read(Files.readString(fixtureRoot().resolve(name))); }
        catch (IOException exception) { throw new IllegalStateException(exception); }
    }

    private static List<Path> fixturePaths() throws IOException {
        try (var stream = Files.list(fixtureRoot())) {
            return stream.filter(path -> path.toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
        }
    }

    private static Path fixtureRoot() {
        Path local = Path.of("src/test/resources/search-integration");
        if (Files.isDirectory(local)) return local;
        return projectRoot().resolve("jc-search-integration/src/test/resources/search-integration");
    }

    private static Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("jc-search-integration")) && Files.isDirectory(current.resolve("jc-backend"))) {
            return current;
        }
        if (current.getFileName() != null && current.getFileName().toString().equals("jc-search-integration")) {
            return current.getParent();
        }
        throw new IllegalStateException("Cannot locate project root from " + current);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void expectFailure(ThrowingRunnable runnable) {
        assertions++;
        try { runnable.run(); }
        catch (RuntimeException expected) { return; }
        throw new AssertionError("Expected failure");
    }

    @FunctionalInterface
    private interface ThrowingRunnable { void run(); }

    private record LegacyEnvelope(List<String> items, int page, int size, long total) {
        private LegacyEnvelope {
            items = List.copyOf(items);
        }
    }

    private record SearchShadowRuntimeInputProviderAdapter(
            com.jc.intelligence.integration.search.v1.SearchShadowRuntimeInputProvider delegate)
            implements com.jc.intelligence.integration.search.v1.SearchShadowRuntimeInputProvider {
        @Override public SearchShadowRuntimeInputResultV1 provide(
                com.jc.intelligence.integration.search.v1.SearchShadowRuntimeInputContextV1 context) {
            return delegate.provide(context);
        }
    }
}
