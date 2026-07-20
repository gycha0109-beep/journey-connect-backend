package com.jc.intelligence.runtime.search;

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
import com.jc.intelligence.contract.v1.search.cursor.SearchCursorValidatorV1;
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
import com.jc.intelligence.runtime.search.v1.DefaultSearchRuntime;
import com.jc.intelligence.runtime.search.v1.SearchRuntimeAuthorityV1;
import com.jc.intelligence.runtime.search.v1.SearchRuntimeExecutionRequestV1;
import com.jc.intelligence.runtime.search.v1.SearchRuntimeFailureCode;
import com.jc.intelligence.runtime.search.v1.SearchRuntimeResultV1;
import com.jc.intelligence.runtime.search.v1.SearchRuntimeStatus;
import com.jc.intelligence.runtime.search.v1.fixture.DeterministicFixtureSearchRankingPort;
import com.jc.intelligence.runtime.search.v1.fixture.InMemorySearchRetrievalPort;
import com.jc.intelligence.runtime.search.v1.fixture.PassThroughSearchCandidateFilter;
import com.jc.intelligence.runtime.search.v1.fixture.SearchRuntimeFixtureCaseV1;
import com.jc.intelligence.runtime.search.v1.fixture.SearchRuntimeFixtureCandidateV1;
import com.jc.intelligence.runtime.search.v1.fixture.SearchRuntimeFixtureJsonCodecV1;
import com.jc.intelligence.runtime.search.v1.port.SearchDependencyDecision;
import com.jc.intelligence.runtime.search.v1.port.SearchRetrievalResultV1;
import com.jc.intelligence.runtime.search.v1.ranking.NoOpSearchRerankingPort;
import com.jc.intelligence.runtime.search.v1.ranking.SearchRankedCandidateV1;
import com.jc.intelligence.runtime.search.v1.ranking.SearchRankingResultV1;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SearchRuntimeContractTest {
    private static final Instant REFERENCE_TIME = Instant.parse("2026-07-19T12:00:00Z");
    private static final Instant STARTED_AT = Instant.parse("2026-07-19T12:00:01Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-07-19T12:00:02Z");
    private static int assertions;

    private SearchRuntimeContractTest() { }

    public static void main(String[] args) throws Exception {
        fixtureContracts();
        runtimeHappyPathContracts();
        noResultsAndBoundaryContracts();
        deterministicOrderingContracts();
        failureAndFallbackContracts();
        filteringEligibilityVisibilityContracts();
        snapshotAndCursorContracts();
        privacyAuthorityAndIsolationContracts();
        wireContracts();
        System.out.println("IP-5 Search runtime foundation assertions: " + assertions + " PASS");
    }

    private static void fixtureContracts() throws IOException {
        List<Path> paths = fixturePaths();
        check(paths.size() == 14, "14 JSON fixtures loaded");
        for (Path path : paths) {
            String json = Files.readString(path);
            SearchRuntimeFixtureCaseV1 fixture = SearchRuntimeFixtureJsonCodecV1.read(json);
            check(fixture.scenario() != null && !fixture.scenario().isBlank(), "fixture scenario present");
            check(fixture.pageSize() >= 1 && fixture.pageSize() <= 100, "fixture page boundary");
            check(fixture.maximumCandidateCount() >= 1 && fixture.maximumCandidateCount() <= 1000,
                    "fixture candidate boundary");
            check(SearchRuntimeFixtureJsonCodecV1.read(SearchRuntimeFixtureJsonCodecV1.write(fixture)).equals(fixture),
                    "fixture JSON round trip " + path.getFileName());
            check(!json.contains("accessToken") && !json.contains("refreshToken") && !json.contains("authorization"),
                    "fixture excludes secrets");
            for (SearchRuntimeFixtureCandidateV1 candidate : fixture.candidates()) {
                check(candidate.entityRef().contains(":"), "fixture entityRef namespaced");
                check(candidate.sourceRank() >= 1, "fixture source rank 1-based");
                check(candidate.rankingScore() == null || Double.isFinite(candidate.rankingScore()),
                        "fixture score finite");
            }
        }
    }

    private static void runtimeHappyPathContracts() throws IOException {
        SearchRuntimeFixtureCaseV1 fixture = load("valid-multiple.json");
        var result = execute(fixture, "run:valid-multiple");
        check(result.status() == SearchRuntimeStatus.SUCCESS, "valid runtime succeeds");
        check(result.snapshot().items().size() == fixture.candidates().size(), "result count preserved");
        check(result.page().items().size() == 2 && result.page().hasNext(), "first page slicing");
        check(result.page().nextCursor() != null && !result.page().nextCursor().productionAuthoritative(),
                "test cursor is non-authoritative");
        check(result.searchRun() != null && result.intelligenceRun() != null, "SearchRun/common run mapped");
        check(result.searchRun().runId().equals(result.intelligenceRun().runId()), "run ID mapping preserved");
        check(result.searchRun().outputSnapshotRef().equals(result.snapshot().snapshotId()),
                "run output snapshot binding");
        check(result.searchRun().candidateSnapshotRef().value().startsWith("snapshot:ephemeral-search-candidates-"),
                "candidate snapshot marked ephemeral");
        check(result.searchRun().inputSnapshotRef().value().startsWith("snapshot:ephemeral-search-input-"),
                "input snapshot marked ephemeral");
        check(result.authority().equals(SearchRuntimeAuthorityV1.foundationOnly()), "foundation-only authority");
        check(result.evidence().candidateCount() == 3 && result.evidence().resultCount() == 3,
                "evidence counts consistent");
        check(result.evidence().snapshotId().equals(result.snapshot().snapshotId()), "evidence snapshot binding");
        check(result.snapshot().requestFingerprint().equals(result.evidence().inputFingerprint()),
                "request fingerprint binding");
        check(result.snapshot().contentHash().matches("[0-9a-f]{64}"), "snapshot content hash");
        check(result.snapshot().snapshotId().value().endsWith(result.snapshot().contentHash()),
                "snapshot ID binds content hash");
        check(result.snapshot().items().get(0).finalPosition() == 1
                && result.snapshot().items().get(2).finalPosition() == 3, "final positions are 1-based");
        check(result.snapshot().items().get(0).candidate().sourceRank() == 1, "source rank preserved");
        check(result.snapshot().items().get(0).rankingScore().equals(0.9d), "ranking score preserved");
        check(result.snapshot().items().get(1).candidate().entityRef().value().equals("post:3"),
                "explicit ordering key tie-break applied");
        check(result.snapshot().items().get(2).candidate().entityRef().value().equals("post:2"),
                "complete deterministic order applied");
    }

    private static void noResultsAndBoundaryContracts() throws IOException {
        var empty = execute(load("empty-result.json"), "run:empty");
        check(empty.status() == SearchRuntimeStatus.NO_RESULTS, "empty result is no_results");
        check(empty.failure() == null && empty.snapshot().items().isEmpty(), "no_results is not failure");
        check(empty.searchRun().status().wireValue().equals("succeeded"), "no_results maps to succeeded run evidence");
        check(!empty.page().hasNext() && empty.page().items().isEmpty(), "empty page projection");

        var single = execute(load("single-candidate.json"), "run:single");
        check(single.status() == SearchRuntimeStatus.SUCCESS, "single candidate success");
        check(single.snapshot().items().size() == 1 && !single.page().hasNext(), "single candidate last page");

        var last = execute(load("last-page.json"), "run:last-page");
        check(last.page().items().size() == 3 && !last.page().hasNext(), "last page no cursor");

        var max = execute(load("max-boundary.json"), "run:max-boundary");
        check(max.status() == SearchRuntimeStatus.SUCCESS, "maximum boundaries accepted");
        check(max.page().pageSize() == 100, "maximum page size accepted");

        SearchRuntimeFixtureCaseV1 overBoundary = new SearchRuntimeFixtureCaseV1(
                "over_boundary", "서울", 20, 1, "success", "success", false,
                List.of(load("valid-multiple.json").candidates().get(0), load("valid-multiple.json").candidates().get(1)));
        var over = execute(overBoundary, "run:over-boundary");
        check(over.status() == SearchRuntimeStatus.FAILED, "candidate boundary violation fails");
        check(over.failure().failureCode() == SearchRuntimeFailureCode.INVALID_CANDIDATE,
                "candidate boundary typed failure");
    }

    private static void deterministicOrderingContracts() throws IOException {
        SearchRuntimeFixtureCaseV1 sameScore = load("same-score.json");
        var first = execute(sameScore, "run:same-score");
        var second = execute(sameScore, "run:same-score");
        check(first.snapshot().equals(second.snapshot()), "repeated execution deterministic snapshot");
        check(first.page().equals(second.page()), "repeated execution deterministic page");
        check(first.searchRun().equals(second.searchRun()), "repeated execution deterministic run");
        check(first.snapshot().items().stream().map(item -> item.candidate().entityRef().value()).toList()
                        .equals(List.of("post:1", "post:2", "post:3")),
                "same score/key falls through source rank and entity ref");

        SearchRuntimeFixtureCaseV1 mixed = load("mixed-entity-types.json");
        var mixedResult = execute(mixed, "run:mixed");
        check(mixedResult.status() == SearchRuntimeStatus.SUCCESS, "mixed entity types supported under ALL scope");
        check(mixedResult.snapshot().items().stream().map(item -> item.candidate().entityType().wireValue()).toList()
                        .equals(List.of("post", "region", "place")),
                "source rank precedes canonical entity type in ordering contract");

        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            var turkish = execute(sameScore, "run:same-score");
            check(turkish.snapshot().items().equals(first.snapshot().items()), "ordering is locale independent");
        } finally {
            Locale.setDefault(previous);
        }

        SearchRuntimeFixtureCaseV1 unicode = load("korean-unicode.json");
        var unicodeResult = execute(unicode, "run:unicode");
        check(unicodeResult.status() == SearchRuntimeStatus.SUCCESS, "Korean Unicode query runtime success");
        check(request(unicode).query().normalizedQuery().equals("서울 여행"), "NFKC/whitespace canonical query preserved");
        check(request(unicode).query().queryFingerprint().equals(unicodeResult.snapshot().queryFingerprint()),
                "normalized query fingerprint bound to snapshot");

        SearchRuntimeFixtureCaseV1 fixture = load("valid-multiple.json");
        Map<EntityRef, Double> scoresOne = new HashMap<>();
        Map<EntityRef, String> keysOne = new HashMap<>();
        for (SearchRuntimeFixtureCandidateV1 c : fixture.candidates()) {
            scoresOne.put(new EntityRef(c.entityRef()), c.rankingScore());
            keysOne.put(new EntityRef(c.entityRef()), c.orderingKey());
        }
        Map<EntityRef, Double> scoresTwo = new LinkedHashMap<>();
        Map<EntityRef, String> keysTwo = new LinkedHashMap<>();
        List<SearchRuntimeFixtureCandidateV1> reverse = new ArrayList<>(fixture.candidates());
        java.util.Collections.reverse(reverse);
        for (SearchRuntimeFixtureCandidateV1 c : reverse) {
            scoresTwo.put(new EntityRef(c.entityRef()), c.rankingScore());
            keysTwo.put(new EntityRef(c.entityRef()), c.orderingKey());
        }
        var resultOne = execute(fixture, "run:map-order", scoresOne, keysOne, new NoOpSearchRerankingPort());
        var resultTwo = execute(fixture, "run:map-order", scoresTwo, keysTwo, new NoOpSearchRerankingPort());
        check(resultOne.snapshot().equals(resultTwo.snapshot()), "HashMap insertion order does not affect output");
    }

    private static void failureAndFallbackContracts() throws IOException {
        var unavailable = execute(load("retrieval-unavailable.json"), "run:retrieval-unavailable");
        check(unavailable.status() == SearchRuntimeStatus.DEPENDENCY_UNAVAILABLE, "retrieval unavailable status");
        check(unavailable.failure().failureCode() == SearchRuntimeFailureCode.RETRIEVAL_UNAVAILABLE,
                "retrieval unavailable typed failure");
        check(unavailable.snapshot() == null && unavailable.searchRun() == null, "failed execution creates no fake run/snapshot");

        var failed = execute(load("retrieval-failed.json"), "run:retrieval-failed");
        check(failed.status() == SearchRuntimeStatus.FAILED, "retrieval failure status");
        check(failed.failure().failureCode() == SearchRuntimeFailureCode.RETRIEVAL_FAILED, "retrieval failure code");

        var fallback = execute(load("ranking-fallback.json"), "run:ranking-fallback");
        check(fallback.status() == SearchRuntimeStatus.FALLBACK, "ranking fallback status");
        check(fallback.fallback() != null && fallback.failure() == null, "fallback not exposed as failure or success");
        check(fallback.searchRun().status().wireValue().equals("fallback"), "SearchRun fallback status");
        check(fallback.searchRun().fallbackCode().wireValue().equals("ranking_failed"), "SearchRun fallback code");
        check(fallback.snapshot().items().stream().allMatch(item -> item.rankingScore() == null),
                "source-rank fallback does not invent ranking score");
        check(fallback.snapshot().items().stream().map(item -> item.candidate().sourceRank()).toList()
                        .equals(List.of(1, 2, 3)), "source-rank fallback ordering");

        SearchRuntimeFixtureCaseV1 noSourceRankFixture = load("ranking-fallback.json");
        List<RetrievalCandidateV1> noSourceRankCandidates = candidates(noSourceRankFixture);
        RetrievalCandidateV1 first = noSourceRankCandidates.get(0);
        noSourceRankCandidates = List.of(new RetrievalCandidateV1(first.contractVersion(), first.entityRef(), first.entityType(),
                first.sourceId(), first.retrievalSource(), first.retrievalScore(), null, first.retrievedAt(),
                first.sourceSnapshotRef(), first.eligibilityState(), first.visibilityState(), first.candidateMetadataRef(),
                first.retrievalStrategyVersion()));
        var fallbackFailure = executeWithPorts(noSourceRankFixture, "run:fallback-failed",
                SearchRetrievalResultV1.success(noSourceRankCandidates), request -> SearchRankingResultV1.failed("fixture"),
                new NoOpSearchRerankingPort(), decisionPort(noSourceRankFixture, true), decisionPort(noSourceRankFixture, false));
        check(fallbackFailure.status() == SearchRuntimeStatus.FAILED, "fallback failure is failed");
        check(fallbackFailure.failure().failureCode() == SearchRuntimeFailureCode.RANKING_FAILED,
                "fallback failure typed ranking failure");

        var invalidScoreNaN = executeWithPorts(load("single-candidate.json"), "run:nan",
                SearchRetrievalResultV1.success(candidates(load("single-candidate.json"))),
                request -> { throw new IllegalArgumentException("rankingScore must be finite when present"); },
                new NoOpSearchRerankingPort(), requestCandidate -> SearchDependencyDecision.ALLOW,
                requestCandidate -> SearchDependencyDecision.ALLOW);
        check(invalidScoreNaN.failure().failureCode() == SearchRuntimeFailureCode.INVALID_SCORE,
                "NaN score typed invalid score");

        var invalidScoreInfinity = executeWithPorts(load("single-candidate.json"), "run:infinity",
                SearchRetrievalResultV1.success(candidates(load("single-candidate.json"))),
                request -> { throw new IllegalArgumentException("rankingScore must be finite when present"); },
                new NoOpSearchRerankingPort(), requestCandidate -> SearchDependencyDecision.ALLOW,
                requestCandidate -> SearchDependencyDecision.ALLOW);
        check(invalidScoreInfinity.failure().failureCode() == SearchRuntimeFailureCode.INVALID_SCORE,
                "Infinity score typed invalid score");

        List<RetrievalCandidateV1> duplicate = new ArrayList<>(candidates(load("single-candidate.json")));
        duplicate.add(duplicate.get(0));
        var duplicateResult = executeWithPorts(load("single-candidate.json"), "run:duplicate",
                SearchRetrievalResultV1.success(duplicate), successfulRanking(load("single-candidate.json")),
                new NoOpSearchRerankingPort(), requestCandidate -> SearchDependencyDecision.ALLOW,
                requestCandidate -> SearchDependencyDecision.ALLOW);
        check(duplicateResult.failure().failureCode() == SearchRuntimeFailureCode.DUPLICATE_CANDIDATE,
                "duplicate candidate typed failure");

        List<RetrievalCandidateV1> nullCandidate = new ArrayList<>();
        nullCandidate.add(null);
        var nullResult = executeWithPorts(load("single-candidate.json"), "run:null-candidate",
                SearchRetrievalResultV1.success(nullCandidate), successfulRanking(load("single-candidate.json")),
                new NoOpSearchRerankingPort(), requestCandidate -> SearchDependencyDecision.ALLOW,
                requestCandidate -> SearchDependencyDecision.ALLOW);
        check(nullResult.failure().failureCode() == SearchRuntimeFailureCode.INVALID_CANDIDATE,
                "null candidate typed failure");

        var rankingThrow = executeWithPorts(load("single-candidate.json"), "run:ranking-throw",
                SearchRetrievalResultV1.success(candidates(load("single-candidate.json"))),
                request -> { throw new IllegalStateException("provider internals"); },
                new NoOpSearchRerankingPort(), requestCandidate -> SearchDependencyDecision.ALLOW,
                requestCandidate -> SearchDependencyDecision.ALLOW);
        check(rankingThrow.failure().failureCode() == SearchRuntimeFailureCode.RANKING_FAILED,
                "ranking exception mapped safely");
        check(!rankingThrow.failure().safeMessage().contains("provider internals"), "raw ranking exception not exposed");

        var rerankThrow = executeWithPorts(load("single-candidate.json"), "run:reranking-throw",
                SearchRetrievalResultV1.success(candidates(load("single-candidate.json"))),
                successfulRanking(load("single-candidate.json")),
                (request, ranked) -> { throw new IllegalStateException("reranker internals"); },
                requestCandidate -> SearchDependencyDecision.ALLOW,
                requestCandidate -> SearchDependencyDecision.ALLOW);
        check(rerankThrow.failure().failureCode() == SearchRuntimeFailureCode.RERANKING_FAILED,
                "reranking exception typed failure");
    }

    private static void filteringEligibilityVisibilityContracts() throws IOException {
        SearchRuntimeFixtureCaseV1 fixture = load("eligibility-filter.json");
        var result = execute(fixture, "run:eligibility");
        check(result.status() == SearchRuntimeStatus.SUCCESS, "eligible candidate remains");
        check(result.snapshot().items().size() == 1, "ineligible/unknown candidates rejected");
        check(result.snapshot().items().get(0).candidate().entityRef().value().equals("post:1"),
                "eligible candidate preserved");
        check(result.evidence().candidateCount() == 3 && result.evidence().eligibleCount() == 1
                && result.evidence().rejectedCount() == 2, "eligibility evidence counts");

        SearchRuntimeFixtureCaseV1 visibility = load("visibility-unavailable.json");
        var unavailable = execute(visibility, "run:visibility-unavailable");
        check(unavailable.status() == SearchRuntimeStatus.DEPENDENCY_UNAVAILABLE,
                "visibility dependency unavailable status");
        check(unavailable.failure().failureCode() == SearchRuntimeFailureCode.VISIBILITY_DEPENDENCY_UNAVAILABLE,
                "visibility dependency typed failure");

        var filterExcluded = executeWithPorts(load("valid-multiple.json"), "run:filter-excluded",
                SearchRetrievalResultV1.success(candidates(load("valid-multiple.json"))),
                successfulRanking(load("valid-multiple.json")), new NoOpSearchRerankingPort(),
                requestCandidate -> SearchDependencyDecision.ALLOW,
                requestCandidate -> SearchDependencyDecision.ALLOW,
                (request, candidate) -> candidate.entityRef().value().equals("post:2")
                        ? com.jc.intelligence.runtime.search.v1.port.SearchFilterDecisionV1.exclude("fixture_excluded")
                        : com.jc.intelligence.runtime.search.v1.port.SearchFilterDecisionV1.include());
        check(filterExcluded.snapshot().items().size() == 2, "candidate filter exclusion applied");
        check(filterExcluded.snapshot().items().stream().noneMatch(item -> item.candidate().entityRef().value().equals("post:2")),
                "filtered entity absent");

        var filterFailure = executeWithPorts(load("single-candidate.json"), "run:filter-failure",
                SearchRetrievalResultV1.success(candidates(load("single-candidate.json"))),
                successfulRanking(load("single-candidate.json")), new NoOpSearchRerankingPort(),
                requestCandidate -> SearchDependencyDecision.ALLOW,
                requestCandidate -> SearchDependencyDecision.ALLOW,
                (request, candidate) -> null);
        check(filterFailure.failure().failureCode() == SearchRuntimeFailureCode.FILTERING_FAILED,
                "null filter decision typed failure");

        var filterThrow = executeWithPorts(load("single-candidate.json"), "run:filter-throw",
                SearchRetrievalResultV1.success(candidates(load("single-candidate.json"))),
                successfulRanking(load("single-candidate.json")), new NoOpSearchRerankingPort(),
                requestCandidate -> SearchDependencyDecision.ALLOW,
                requestCandidate -> SearchDependencyDecision.ALLOW,
                (request, candidate) -> { throw new IllegalStateException("filter internals"); });
        check(filterThrow.failure().failureCode() == SearchRuntimeFailureCode.FILTERING_FAILED,
                "filter exception typed failure");

        var eligibilityThrow = executeWithPorts(load("single-candidate.json"), "run:eligibility-throw",
                SearchRetrievalResultV1.success(candidates(load("single-candidate.json"))),
                successfulRanking(load("single-candidate.json")), new NoOpSearchRerankingPort(),
                requestCandidate -> { throw new IllegalStateException("eligibility internals"); },
                requestCandidate -> SearchDependencyDecision.ALLOW);
        check(eligibilityThrow.failure().failureCode() == SearchRuntimeFailureCode.ELIGIBILITY_DEPENDENCY_UNAVAILABLE,
                "eligibility exception typed dependency failure");

        var visibilityThrow = executeWithPorts(load("single-candidate.json"), "run:visibility-throw",
                SearchRetrievalResultV1.success(candidates(load("single-candidate.json"))),
                successfulRanking(load("single-candidate.json")), new NoOpSearchRerankingPort(),
                requestCandidate -> SearchDependencyDecision.ALLOW,
                requestCandidate -> { throw new IllegalStateException("visibility internals"); });
        check(visibilityThrow.failure().failureCode() == SearchRuntimeFailureCode.VISIBILITY_DEPENDENCY_UNAVAILABLE,
                "visibility exception typed dependency failure");

        SearchRuntimeFixtureCaseV1 rerank = load("reranking-change.json");
        var noOp = execute(rerank, "run:no-op", scores(rerank), keys(rerank), new NoOpSearchRerankingPort());
        var changed = execute(rerank, "run:rerank-change", scores(rerank), keys(rerank),
                (request, ranked) -> {
                    List<SearchRankedCandidateV1> copy = new ArrayList<>();
                    for (int index = 0; index < ranked.size(); index++) {
                        SearchRankedCandidateV1 item = ranked.get(index);
                        copy.add(new SearchRankedCandidateV1(item.candidate(), item.rankingScore(),
                                "rerank-" + (ranked.size() - index)));
                    }
                    return copy;
                });
        check(noOp.snapshot().items().size() == changed.snapshot().items().size(), "reranker preserves candidate count");
        check(changed.snapshot().items().stream().allMatch(item -> item.explicitOrderingKey().startsWith("rerank-")),
                "reranking output feeds deterministic ordering");
    }

    private static void snapshotAndCursorContracts() throws IOException {
        SearchRuntimeFixtureCaseV1 fixture = load("valid-multiple.json");
        var result = execute(fixture, "run:cursor");
        var cursor = result.page().nextCursor().cursor();
        SearchCursorValidatorV1.validateChecksum(cursor);
        SearchCursorValidatorV1.validateBinding(cursor, result.snapshot().queryFingerprint(),
                result.snapshot().filterFingerprint(), result.snapshot().rankingPolicyVersion(),
                request(fixture).context().surface(), request(fixture).context().entityScope(),
                request(fixture).context().subjectRef(), request(fixture).context().sessionRef(),
                request(fixture).context().referenceTime());
        check(cursor.resultSnapshotRef().equals(result.snapshot().snapshotId()), "cursor snapshot bound");
        check(cursor.nextRank() == 3, "cursor next rank after first page");
        expectFailure(() -> SearchCursorValidatorV1.validateBinding(cursor, "0".repeat(64),
                result.snapshot().filterFingerprint(), result.snapshot().rankingPolicyVersion(),
                request(fixture).context().surface(), request(fixture).context().entityScope(),
                request(fixture).context().subjectRef(), request(fixture).context().sessionRef(),
                request(fixture).context().referenceTime()));

        List<com.jc.intelligence.runtime.search.v1.snapshot.SearchResultItemV1> source =
                new ArrayList<>(result.snapshot().items());
        expectFailure(() -> result.snapshot().items().add(source.get(0)));
        source.clear();
        check(result.snapshot().items().size() == 3, "snapshot defensive copy");
        expectFailure(() -> result.page().items().clear());

        SearchRuntimeFixtureCaseV1 differentQuery = new SearchRuntimeFixtureCaseV1(
                fixture.scenario(), "Different Query", fixture.pageSize(), fixture.maximumCandidateCount(),
                fixture.retrievalStatus(), fixture.rankingStatus(), fixture.reverseReranking(), fixture.candidates());
        var different = execute(differentQuery, "run:cursor");
        check(!different.snapshot().snapshotId().equals(result.snapshot().snapshotId()),
                "snapshot ID binds query/request context");

        SearchRequestV1 cursorRequest = new SearchRequestV1(SearchContractIds.SEARCH_DOMAIN, "request:cursor-reuse",
                "correlation:cursor-reuse", request(fixture).query(), request(fixture).context(), List.of(),
                request(fixture).sort(), new SearchPageRequestV1(2, cursor), new SchemaVersion("search-request-v1"),
                request(fixture).queryNormalizationVersion(), request(fixture).rankingPolicyVersion(),
                request(fixture).featureDefinitionVersion());
        expectFailure(() -> new SearchRuntimeExecutionRequestV1(cursorRequest, new RunRef("run:cursor-reuse"),
                new SchemaVersion("search-runtime-foundation-v1"), new SchemaVersion("search-retrieval-fixture-v1"),
                List.of(RetrievalSource.DATABASE_POST), new PolicyVersion("search-fallback-foundation-v1"),
                new ProducerBuildId("ip5-test-build"), STARTED_AT, COMPLETED_AT, 10, true));
    }

    private static void privacyAuthorityAndIsolationContracts() throws IOException {
        var result = execute(load("valid-multiple.json"), "run:privacy");
        check(!result.authority().persistenceAuthority(), "no persistence authority");
        check(!result.authority().exposureAuthority(), "no exposure authority");
        check(!result.authority().productionCursorAuthority(), "no production cursor authority");
        check(!result.authority().apiCutoverAuthority(), "no API cutover authority");
        check(result.searchRun().reservedExposureSourceId().wireValue().equals("search_exposure_v1"),
                "reserved exposure ID remains semantic only");
        check(result.evidence().getClass().getRecordComponents() != null, "evidence is immutable record");
        Set<String> evidenceFields = java.util.Arrays.stream(result.evidence().getClass().getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).collect(java.util.stream.Collectors.toSet());
        check(!evidenceFields.contains("rawQuery") && !evidenceFields.contains("originalQuery")
                && !evidenceFields.contains("candidatePayload"), "runtime evidence excludes raw/private payloads");

        Path project = projectRoot();
        Path sourceRoot = project.resolve("jc-search-runtime/src/main/java");
        try (var stream = Files.walk(sourceRoot)) {
            stream.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    String source = Files.readString(path);
                    for (String forbidden : List.of("com.jc.backend", "com.jc.recommendation", "org.springframework",
                            "jakarta.persistence", "java.sql", "javax.sql", "@Component", "@Service", "@Repository",
                            "@Configuration", "@Bean", "jc-search-compatibility")) {
                        check(!source.contains(forbidden), path.getFileName() + " excludes " + forbidden);
                    }
                    check(!source.contains("currentTimeMillis") && !source.contains("Instant.now()")
                            && !source.contains("UUID.randomUUID"), path.getFileName() + " excludes time/random ordering");
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            });
        }
        String build = Files.readString(project.resolve("jc-search-runtime/build.gradle.kts"));
        check(build.contains("api(project(\":jc-search-contracts\"))"), "runtime depends on Search contracts");
        check(!build.contains("jc-backend") && !build.contains("jc-recommendation-core")
                && !build.contains("jc-search-compatibility"), "runtime build excludes forbidden modules");
        String settings = Files.readString(project.resolve("jc-backend/settings.gradle.kts"));
        check(settings.contains("include(\":jc-search-runtime\")"), "runtime module registered");
        Path backendSearch = project.resolve("jc-backend/src/main/java/com/jc/backend/search");
        check(Files.isDirectory(backendSearch.resolve("shadow"))
                        && !Files.exists(backendSearch.resolve("runtime"))
                        && !Files.exists(backendSearch.resolve("persistence")),
                "backend contains only the IP-9 controlled disabled shadow boundary");
    }

    private static void wireContracts() {
        List<Class<? extends Enum<?>>> enums = List.of(
                SearchRuntimeStatus.class,
                SearchRuntimeFailureCode.class,
                com.jc.intelligence.runtime.search.v1.SearchRuntimeFallbackCode.class,
                com.jc.intelligence.runtime.search.v1.port.SearchDependencyDecision.class,
                com.jc.intelligence.runtime.search.v1.port.SearchRetrievalStatus.class,
                com.jc.intelligence.runtime.search.v1.ranking.SearchRankingStatus.class);
        for (Class<? extends Enum<?>> type : enums) {
            for (Enum<?> item : type.getEnumConstants()) {
                check(item instanceof WireValue, "runtime enum implements WireValue");
                check(((WireValue) item).wireValue().matches("[a-z][a-z0-9_]*"),
                        type.getSimpleName() + " lowercase_snake_case");
            }
        }
        expectFailure(() -> new SearchRuntimeAuthorityV1("foundation_only", true, false, false, false));
        expectFailure(() -> new SearchRuntimeAuthorityV1("production", false, false, false, false));
        SearchRuntimeFixtureCaseV1 fixture;
        try { fixture = load("single-candidate.json"); } catch (IOException exception) { throw new IllegalStateException(exception); }
        SearchRequestV1 request = request(fixture);
        expectFailure(() -> new SearchRuntimeExecutionRequestV1(request, new RunRef("run:external-provider"),
                new SchemaVersion("search-runtime-foundation-v1"), new SchemaVersion("search-retrieval-fixture-v1"),
                List.of(RetrievalSource.EXTERNAL_PROVIDER), new PolicyVersion("search-fallback-foundation-v1"),
                new ProducerBuildId("ip5-test-build"), STARTED_AT, COMPLETED_AT, 10, true));
    }

    private static SearchRuntimeResultV1 execute(SearchRuntimeFixtureCaseV1 fixture, String runId) {
        return execute(fixture, runId, scores(fixture), keys(fixture), fixture.reverseReranking()
                ? (request, ranked) -> {
                    List<SearchRankedCandidateV1> copy = new ArrayList<>(ranked);
                    java.util.Collections.reverse(copy);
                    return copy;
                }
                : new NoOpSearchRerankingPort());
    }

    private static SearchRuntimeResultV1 execute(
            SearchRuntimeFixtureCaseV1 fixture,
            String runId,
            Map<EntityRef, Double> scores,
            Map<EntityRef, String> keys,
            com.jc.intelligence.runtime.search.v1.ranking.SearchRerankingPort reranker) {
        SearchRetrievalResultV1 retrieval = switch (fixture.retrievalStatus()) {
            case "success" -> SearchRetrievalResultV1.success(candidates(fixture));
            case "unavailable" -> SearchRetrievalResultV1.unavailable("fixture_unavailable");
            case "failed" -> SearchRetrievalResultV1.failed("fixture_failed");
            default -> throw new IllegalArgumentException("unknown fixture retrieval status");
        };
        var ranking = "failed".equals(fixture.rankingStatus())
                ? (com.jc.intelligence.runtime.search.v1.ranking.SearchRankingPort) request -> SearchRankingResultV1.failed("fixture_failed")
                : new DeterministicFixtureSearchRankingPort(scores, keys);
        return executeWithPorts(fixture, runId, retrieval, ranking, reranker,
                decisionPort(fixture, true), decisionPort(fixture, false));
    }

    private static SearchRuntimeResultV1 executeWithPorts(
            SearchRuntimeFixtureCaseV1 fixture,
            String runId,
            SearchRetrievalResultV1 retrieval,
            com.jc.intelligence.runtime.search.v1.ranking.SearchRankingPort ranking,
            com.jc.intelligence.runtime.search.v1.ranking.SearchRerankingPort reranker,
            SimpleDecisionPort eligibility,
            SimpleDecisionPort visibility) {
        return executeWithPorts(fixture, runId, retrieval, ranking, reranker, eligibility, visibility,
                new PassThroughSearchCandidateFilter());
    }

    private static SearchRuntimeResultV1 executeWithPorts(
            SearchRuntimeFixtureCaseV1 fixture,
            String runId,
            SearchRetrievalResultV1 retrieval,
            com.jc.intelligence.runtime.search.v1.ranking.SearchRankingPort ranking,
            com.jc.intelligence.runtime.search.v1.ranking.SearchRerankingPort reranker,
            SimpleDecisionPort eligibility,
            SimpleDecisionPort visibility,
            com.jc.intelligence.runtime.search.v1.port.SearchCandidateFilter filter) {
        DefaultSearchRuntime runtime = new DefaultSearchRuntime(new InMemorySearchRetrievalPort(retrieval), filter,
                (request, candidate) -> eligibility.decide(candidate),
                (request, candidate) -> visibility.decide(candidate), ranking, reranker);
        return runtime.execute(execution(fixture, runId));
    }

    private static SearchRuntimeExecutionRequestV1 execution(SearchRuntimeFixtureCaseV1 fixture, String runId) {
        List<RetrievalSource> sources = candidates(fixture).stream().map(RetrievalCandidateV1::retrievalSource).distinct().toList();
        if (sources.isEmpty()) sources = List.of(RetrievalSource.DATABASE_POST);
        return new SearchRuntimeExecutionRequestV1(request(fixture), new RunRef(runId),
                new SchemaVersion("search-runtime-foundation-v1"), new SchemaVersion("search-retrieval-fixture-v1"),
                sources, new PolicyVersion("search-fallback-foundation-v1"), new ProducerBuildId("ip5-test-build"),
                STARTED_AT, COMPLETED_AT, fixture.maximumCandidateCount(), true);
    }

    private static SearchRequestV1 request(SearchRuntimeFixtureCaseV1 fixture) {
        var query = SearchQueryCanonicalizerV1.canonicalize(SearchQueryMode.TEXT_QUERY, fixture.rawQuery(), "ko", "ko-KR");
        SearchEntityScope scope = fixture.candidates().stream().map(SearchRuntimeFixtureCandidateV1::entityRef)
                .map(ref -> ref.substring(0, ref.indexOf(':'))).distinct().count() > 1 ? SearchEntityScope.ALL : SearchEntityScope.POST;
        PolicyVersion rankingPolicy = new PolicyVersion("search-ranking-foundation-v1");
        return new SearchRequestV1(SearchContractIds.SEARCH_DOMAIN, "request:ip5", "correlation:ip5", query,
                new SearchContextV1(null, "session:ip5", SearchSurface.GLOBAL_SEARCH, scope, REFERENCE_TIME,
                        "ko", "ko-KR", null, null), List.of(), new SearchSortV1(SearchSortType.RELEVANCE, rankingPolicy),
                SearchPageRequestV1.firstPage(fixture.pageSize()), new SchemaVersion("search-request-v1"),
                query.normalizationVersion(), rankingPolicy, new FeatureDefinitionVersion("search-feature-foundation-v1"));
    }

    private static List<RetrievalCandidateV1> candidates(SearchRuntimeFixtureCaseV1 fixture) {
        List<RetrievalCandidateV1> result = new ArrayList<>();
        for (SearchRuntimeFixtureCandidateV1 item : fixture.candidates()) {
            EntityRef ref = new EntityRef(item.entityRef());
            SearchEntityType type = SearchEntityType.fromWire(ref.entityType());
            result.add(new RetrievalCandidateV1(SearchContractIds.SEARCH_RETRIEVAL_RANKING, ref, type, ref.sourceId(),
                    retrievalSource(type), null, Integer.valueOf(item.sourceRank()), REFERENCE_TIME,
                    new SnapshotRef("snapshot:fixture-source-v1"), SearchEligibilityState.UNKNOWN,
                    SearchVisibilityState.UNKNOWN, null, new SchemaVersion("search-retrieval-fixture-v1")));
        }
        return result;
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

    private static Map<EntityRef, Double> scores(SearchRuntimeFixtureCaseV1 fixture) {
        Map<EntityRef, Double> result = new LinkedHashMap<>();
        for (SearchRuntimeFixtureCandidateV1 item : fixture.candidates()) result.put(new EntityRef(item.entityRef()), item.rankingScore());
        return result;
    }

    private static Map<EntityRef, String> keys(SearchRuntimeFixtureCaseV1 fixture) {
        Map<EntityRef, String> result = new LinkedHashMap<>();
        for (SearchRuntimeFixtureCandidateV1 item : fixture.candidates()) {
            if (item.orderingKey() != null) result.put(new EntityRef(item.entityRef()), item.orderingKey());
        }
        return result;
    }

    private static com.jc.intelligence.runtime.search.v1.ranking.SearchRankingPort successfulRanking(SearchRuntimeFixtureCaseV1 fixture) {
        return new DeterministicFixtureSearchRankingPort(scores(fixture), keys(fixture));
    }

    private static SimpleDecisionPort decisionPort(SearchRuntimeFixtureCaseV1 fixture, boolean eligibility) {
        Map<EntityRef, SearchDependencyDecision> decisions = new HashMap<>();
        for (SearchRuntimeFixtureCandidateV1 item : fixture.candidates()) {
            String wire = eligibility ? item.eligibilityDecision() : item.visibilityDecision();
            decisions.put(new EntityRef(item.entityRef()), switch (wire) {
                case "allow" -> SearchDependencyDecision.ALLOW;
                case "deny" -> SearchDependencyDecision.DENY;
                case "unknown" -> SearchDependencyDecision.UNKNOWN;
                case "dependency_unavailable" -> SearchDependencyDecision.DEPENDENCY_UNAVAILABLE;
                default -> throw new IllegalArgumentException("unknown dependency decision");
            });
        }
        return candidate -> decisions.getOrDefault(candidate.entityRef(), SearchDependencyDecision.UNKNOWN);
    }

    private static List<Path> fixturePaths() throws IOException {
        try (var stream = Files.list(fixtureRoot())) {
            return stream.filter(path -> path.toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
        }
    }

    private static SearchRuntimeFixtureCaseV1 load(String name) throws IOException {
        return SearchRuntimeFixtureJsonCodecV1.read(Files.readString(fixtureRoot().resolve(name)));
    }

    private static Path fixtureRoot() {
        Path local = Path.of("src/test/resources/search-runtime");
        if (Files.isDirectory(local)) return local;
        return projectRoot().resolve("jc-search-runtime/src/test/resources/search-runtime");
    }

    private static Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("jc-search-runtime")) && Files.isDirectory(current.resolve("jc-backend"))) return current;
        if (current.getFileName() != null && current.getFileName().toString().equals("jc-search-runtime")) return current.getParent();
        throw new IllegalStateException("Cannot locate project root from " + current);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void expectFailure(ThrowingRunnable runnable) {
        assertions++;
        try { runnable.run(); }
        catch (RuntimeException | IOException expected) { return; }
        throw new AssertionError("Expected failure");
    }

    @FunctionalInterface
    private interface ThrowingRunnable { void run() throws IOException; }

    @FunctionalInterface
    private interface SimpleDecisionPort { SearchDependencyDecision decide(RetrievalCandidateV1 candidate); }
}
