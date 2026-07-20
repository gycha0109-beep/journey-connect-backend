package com.jc.intelligence.contract.search;

import com.jc.intelligence.contract.v1.explanation.ExplanationAudience;
import com.jc.intelligence.contract.v1.identity.EntityRef;
import com.jc.intelligence.contract.v1.identity.RunRef;
import com.jc.intelligence.contract.v1.identity.SnapshotRef;
import com.jc.intelligence.contract.v1.identity.SubjectRef;
import com.jc.intelligence.contract.v1.replay.ReplayClass;
import com.jc.intelligence.contract.v1.replay.ReplayEvidenceDescriptorV1;
import com.jc.intelligence.contract.v1.run.IntelligenceRunStatus;
import com.jc.intelligence.contract.v1.run.IntelligenceRunType;
import com.jc.intelligence.contract.v1.search.RetrievalSource;
import com.jc.intelligence.contract.v1.search.SearchContractIds;
import com.jc.intelligence.contract.v1.search.SearchEligibilityState;
import com.jc.intelligence.contract.v1.search.SearchEntityScope;
import com.jc.intelligence.contract.v1.search.SearchEntityType;
import com.jc.intelligence.contract.v1.search.SearchExplanationReason;
import com.jc.intelligence.contract.v1.search.SearchFailureCode;
import com.jc.intelligence.contract.v1.search.SearchFallbackCode;
import com.jc.intelligence.contract.v1.search.SearchFilterSource;
import com.jc.intelligence.contract.v1.search.SearchFilterType;
import com.jc.intelligence.contract.v1.search.SearchQueryMode;
import com.jc.intelligence.contract.v1.search.SearchSortType;
import com.jc.intelligence.contract.v1.search.SearchSurface;
import com.jc.intelligence.contract.v1.search.SearchVisibilityState;
import com.jc.intelligence.contract.v1.search.cursor.SearchCursorCodecV1;
import com.jc.intelligence.contract.v1.search.cursor.SearchCursorFactoryV1;
import com.jc.intelligence.contract.v1.search.cursor.SearchCursorV1;
import com.jc.intelligence.contract.v1.search.cursor.SearchCursorValidatorV1;
import com.jc.intelligence.contract.v1.search.cursor.SearchOrderingTupleV1;
import com.jc.intelligence.contract.v1.search.explanation.SearchExplanationV1;
import com.jc.intelligence.contract.v1.search.query.SearchContextV1;
import com.jc.intelligence.contract.v1.search.query.SearchFilterCanonicalizerV1;
import com.jc.intelligence.contract.v1.search.query.SearchFilterV1;
import com.jc.intelligence.contract.v1.search.query.SearchPageRequestV1;
import com.jc.intelligence.contract.v1.search.query.SearchQueryCanonicalizerV1;
import com.jc.intelligence.contract.v1.search.query.SearchQueryV1;
import com.jc.intelligence.contract.v1.search.query.SearchRequestV1;
import com.jc.intelligence.contract.v1.search.query.SearchSortV1;
import com.jc.intelligence.contract.v1.search.query.SearchSortValidatorV1;
import com.jc.intelligence.contract.v1.search.retrieval.RetrievalCandidateV1;
import com.jc.intelligence.contract.v1.search.retrieval.RetrievalRequestV1;
import com.jc.intelligence.contract.v1.search.run.SearchRunV1;
import com.jc.intelligence.contract.v1.search.serialization.SearchContractJsonCodecV1;
import com.jc.intelligence.contract.v1.search.validation.SearchContractValidationException;
import com.jc.intelligence.contract.v1.search.validation.SearchQueryValidatorV1;
import com.jc.intelligence.contract.v1.search.validation.SearchValidationErrorCode;
import com.jc.intelligence.contract.v1.snapshot.PrivacyClass;
import com.jc.intelligence.contract.v1.version.ContractId;
import com.jc.intelligence.contract.v1.version.FeatureDefinitionVersion;
import com.jc.intelligence.contract.v1.version.PolicyVersion;
import com.jc.intelligence.contract.v1.version.ProducerBuildId;
import com.jc.intelligence.contract.v1.version.SchemaVersion;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class SearchDomainContractsContractTest {
    private static int assertions;

    private SearchDomainContractsContractTest() { }

    public static void main(String[] args) throws Exception {
        queryValidationAndCanonicalization();
        filterSortAndBuilderValidation();
        cursorContract();
        serializationFixtures();
        retrievalAndRunContracts();
        explanationAndWireEnums();
        isolationAndSensitiveFixtureScan();
        System.out.println("Search Domain contract assertions PASS: " + assertions);
    }

    private static void queryValidationAndCanonicalization() {
        SearchQueryV1 first = SearchQueryCanonicalizerV1.canonicalize(
                SearchQueryMode.TEXT_QUERY, "  ＳＥＯＵＬ　Cafe  ", "ko", "ko-KR");
        SearchQueryV1 second = SearchQueryCanonicalizerV1.canonicalize(
                SearchQueryMode.TEXT_QUERY, "seoul   CAFE", "ko", "ko-KR");
        check("seoul cafe".equals(first.normalizedQuery()), "NFKC/whitespace/case normalization");
        check(first.queryFingerprint().equals(second.queryFingerprint()), "canonical fingerprint equality");
        check(!first.originalQuery().equals(first.normalizedQuery()), "raw and normalized query separated");
        SearchQueryV1 browse = SearchQueryCanonicalizerV1.canonicalize(SearchQueryMode.BROWSE, null, null, null);
        check(browse.normalizedQuery() == null && browse.codePointLength() == 0, "browse query absence");
        expectCode(SearchValidationErrorCode.SEARCH_QUERY_BLANK, () ->
                SearchQueryCanonicalizerV1.canonicalize(SearchQueryMode.TEXT_QUERY, "   ", null, null));
        expectCode(SearchValidationErrorCode.SEARCH_QUERY_BLANK, () ->
                SearchQueryCanonicalizerV1.canonicalize(SearchQueryMode.BROWSE, "   ", null, null));
        String max = "가".repeat(SearchQueryValidatorV1.MAX_CODE_POINTS);
        check(SearchQueryCanonicalizerV1.canonicalize(SearchQueryMode.TEXT_QUERY, max, null, null).codePointLength() == 256,
                "query max boundary");
        expectCode(SearchValidationErrorCode.SEARCH_QUERY_TOO_LONG, () ->
                SearchQueryCanonicalizerV1.canonicalize(SearchQueryMode.TEXT_QUERY, max + "가", null, null));
        expectCode(SearchValidationErrorCode.SEARCH_QUERY_UNSUPPORTED_CHARACTER, () ->
                SearchQueryCanonicalizerV1.canonicalize(SearchQueryMode.TEXT_QUERY, "safe\u202Eunsafe", null, null));
        expectCode(SearchValidationErrorCode.SEARCH_QUERY_UNSUPPORTED_CHARACTER, () ->
                SearchQueryCanonicalizerV1.canonicalize(SearchQueryMode.TEXT_QUERY, "x\uD800", null, null));
    }

    private static void filterSortAndBuilderValidation() throws IOException {
        SearchRequestV1 request = SearchContractJsonCodecV1.readRequest(fixture("search-request-v1-valid.json"));
        check(request.filters().size() == 2, "fixture filters loaded");
        expectUnsupported(() -> request.filters().add(request.filters().get(0)));
        SearchFilterV1 duplicateRegion = new SearchFilterV1(SearchFilterType.REGION,
                List.of("KR-11", "kr-11"), SearchFilterSource.USER_SELECTED, new SchemaVersion("search-filter-v1"));
        check(duplicateRegion.values().equals(List.of("kr-11")), "filter value canonical dedupe");
        String fp1 = SearchFilterCanonicalizerV1.fingerprint(request.filters());
        String fp2 = SearchFilterCanonicalizerV1.fingerprint(List.of(request.filters().get(1), request.filters().get(0)));
        check(fp1.equals(fp2), "filter fingerprint order independence");
        expectCode(SearchValidationErrorCode.SEARCH_QUERY_UNSUPPORTED_CHARACTER, () ->
                new SearchFilterV1(SearchFilterType.TAG, List.of("safe\u0000unsafe"),
                        SearchFilterSource.USER_SELECTED, new SchemaVersion("search-filter-v1")));
        SearchContextV1 uuidSessionContext = new SearchContextV1(null,
                "550e8400-e29b-41d4-a716-446655440000", SearchSurface.EXPLORE,
                SearchEntityScope.POST, Instant.parse("2026-07-19T12:00:00Z"), null, null, null, null);
        check(uuidSessionContext.sessionRef().startsWith("550e8400"), "UUID session identifiers remain valid");
        expectCode(SearchValidationErrorCode.SEARCH_FILTER_INVALID, () ->
                SearchFilterCanonicalizerV1.canonicalize(List.of(
                        new SearchFilterV1(SearchFilterType.LANGUAGE, List.of("ko"), SearchFilterSource.USER_SELECTED,
                                new SchemaVersion("search-filter-v1")),
                        new SearchFilterV1(SearchFilterType.LANGUAGE, List.of("en"), SearchFilterSource.USER_SELECTED,
                                new SchemaVersion("search-filter-v1")))));
        SearchSortValidatorV1.validate(request.sort(), request.query(), request.context().entityScope());
        expectCode(SearchValidationErrorCode.SEARCH_SORT_INVALID, () -> SearchSortValidatorV1.validate(
                new SearchSortV1(SearchSortType.DISTANCE, new PolicyVersion("search-sort-policy-v1")),
                request.query(), SearchEntityScope.POST));
        expectCode(SearchValidationErrorCode.SEARCH_NULL_INVALID, () -> SearchRequestV1.builder()
                .contractVersion(SearchContractIds.SEARCH_DOMAIN).build());
        check(SearchContractJsonCodecV1.readRequest(SearchContractJsonCodecV1.writeRequest(request)).equals(request),
                "request JSON round trip");
        expectFailure(() -> SearchContractJsonCodecV1.readRequest(fixture("search-request-v1-invalid.json")));
    }

    private static void cursorContract() throws IOException {
        SearchCursorV1 fixtureCursor = SearchContractJsonCodecV1.readCursor(fixture("search-cursor-v1-valid.json"));
        SearchCursorValidatorV1.validateChecksum(fixtureCursor);
        String encoded = SearchCursorCodecV1.encode(fixtureCursor);
        SearchCursorV1 decoded = SearchCursorCodecV1.decode(encoded);
        check(decoded.equals(fixtureCursor), "cursor serialize/parse round trip");
        check(!new String(java.util.Base64.getUrlDecoder().decode(encoded), java.nio.charset.StandardCharsets.UTF_8)
                .contains("offset"), "cursor is not an offset wrapper");
        check(decoded.searchRunId().value().equals("search-run-001")
                && decoded.resultSnapshotRef().value().equals("search-output-snapshot-001"),
                "cursor run/snapshot binding");
        expectCode(SearchValidationErrorCode.SEARCH_CURSOR_EXPIRED, () ->
                SearchCursorValidatorV1.validateExpiration(decoded, decoded.expiresAt().plusSeconds(1)));
        expectCode(SearchValidationErrorCode.SEARCH_CURSOR_MISMATCH, () -> SearchCursorValidatorV1.validateBinding(
                decoded, "a".repeat(64), decoded.filterFingerprint(), decoded.rankingPolicyVersion(),
                decoded.surface(), decoded.entityScope(), decoded.subjectBindingRef(), decoded.sessionBindingRef(),
                decoded.referenceTime()));
        expectCode(SearchValidationErrorCode.SEARCH_CURSOR_INVALID, () -> new SearchCursorV1(
                decoded.cursorVersion(), decoded.searchRunId(), decoded.resultSnapshotRef(),
                decoded.queryFingerprint(), decoded.filterFingerprint(), decoded.sortPolicyVersion(),
                decoded.rankingPolicyVersion(), decoded.referenceTime(), decoded.nextRank() + 1,
                decoded.lastOrderingTuple(), decoded.surface(), decoded.entityScope(),
                decoded.subjectBindingRef(), decoded.sessionBindingRef(), decoded.issuedAt(),
                decoded.expiresAt(), decoded.checksum()));
        SearchCursorV1 recreated = SearchCursorFactoryV1.create(decoded.cursorVersion(), decoded.searchRunId(),
                decoded.resultSnapshotRef(), decoded.queryFingerprint(), decoded.filterFingerprint(),
                decoded.sortPolicyVersion(), decoded.rankingPolicyVersion(), decoded.referenceTime(),
                decoded.nextRank(), decoded.lastOrderingTuple(), decoded.surface(), decoded.entityScope(),
                decoded.subjectBindingRef(), decoded.sessionBindingRef(), decoded.issuedAt(), decoded.expiresAt());
        check(recreated.equals(decoded), "cursor deterministic checksum factory");
    }

    private static void serializationFixtures() throws IOException {
        SearchQueryV1 query = SearchContractJsonCodecV1.readQuery(fixture("search-query-v1-normalized.json"));
        check(SearchContractJsonCodecV1.readQuery(SearchContractJsonCodecV1.writeQuery(query)).equals(query),
                "query JSON round trip");
        var failure = SearchContractJsonCodecV1.readFailure(fixture("search-failure-v1-valid.json"));
        check(SearchContractJsonCodecV1.readFailure(SearchContractJsonCodecV1.writeFailure(failure)).equals(failure),
                "failure JSON round trip");
        var fallback = SearchContractJsonCodecV1.readFallback(fixture("search-fallback-v1-valid.json"));
        check(SearchContractJsonCodecV1.readFallback(SearchContractJsonCodecV1.writeFallback(fallback)).equals(fallback),
                "fallback JSON round trip");
        RetrievalCandidateV1 candidate = SearchContractJsonCodecV1.readRetrievalCandidate(
                fixture("retrieval-candidate-v1-valid.json"));
        check(SearchContractJsonCodecV1.readRetrievalCandidate(
                SearchContractJsonCodecV1.writeRetrievalCandidate(candidate)).equals(candidate),
                "retrieval candidate JSON round trip");
        SearchRunV1 run = SearchContractJsonCodecV1.readRun(fixture("search-run-v1-valid.json"));
        check(SearchContractJsonCodecV1.readRun(SearchContractJsonCodecV1.writeRun(run)).equals(run),
                "search run JSON round trip");
        String unknown = fixture("search-run-v1-valid.json").replaceFirst("\\{", "{\"futureField\":\"ignored\",");
        check(SearchContractJsonCodecV1.readRun(unknown).equals(run), "unknown optional field ignored");
        expectFailure(() -> SearchContractJsonCodecV1.readRun(
                fixture("search-run-v1-valid.json").replace("\"succeeded\"", "\"unknown_status\"")));
        String requestJson = fixture("search-request-v1-valid.json");
        check(requestJson.contains("\"queryMode\": \"text_query\"") && !requestJson.contains("\"queryMode\": 0"),
                "wire enums are strings");
        check(requestJson.contains("\"referenceTime\": \"2026-07-19T12:00:00Z\""), "UTC Instant JSON");
    }

    private static void retrievalAndRunContracts() throws IOException {
        RetrievalCandidateV1 candidate = SearchContractJsonCodecV1.readRetrievalCandidate(
                fixture("retrieval-candidate-v1-valid.json"));
        check(candidate.retrievalScore() != null && candidate.sourceRank() == 1, "candidate optional score/rank");
        expectCode(SearchValidationErrorCode.SEARCH_SCORE_INVALID, () -> new RetrievalCandidateV1(
                SearchContractIds.SEARCH_RETRIEVAL_RANKING, new EntityRef("post:1"), SearchEntityType.POST, "1",
                RetrievalSource.DATABASE_POST, Double.NaN, 1, Instant.parse("2026-07-19T12:00:00Z"),
                new SnapshotRef("source-snapshot-1"), SearchEligibilityState.ELIGIBLE,
                SearchVisibilityState.VISIBLE, null, new SchemaVersion("database-lexical-v1")));
        expectCode(SearchValidationErrorCode.SEARCH_RANK_INVALID, () -> new RetrievalCandidateV1(
                SearchContractIds.SEARCH_RETRIEVAL_RANKING, new EntityRef("post:1"), SearchEntityType.POST, "1",
                RetrievalSource.DATABASE_POST, null, 0, Instant.parse("2026-07-19T12:00:00Z"),
                new SnapshotRef("source-snapshot-1"), SearchEligibilityState.ELIGIBLE,
                SearchVisibilityState.VISIBLE, null, new SchemaVersion("database-lexical-v1")));
        RetrievalRequestV1 retrievalRequest = new RetrievalRequestV1(
                SearchContractIds.SEARCH_RETRIEVAL_RANKING, new RunRef("search-run-001"),
                SearchQueryCanonicalizerV1.canonicalize(SearchQueryMode.TEXT_QUERY, "Seoul", null, null),
                SearchEntityScope.POST, List.of(), new SchemaVersion("database-lexical-v1"),
                List.of(RetrievalSource.DATABASE_POST), Instant.parse("2026-07-19T12:00:00Z"),
                100, new SnapshotRef("visibility-snapshot-001"), new ProducerBuildId("ip3-build-001"));
        check(retrievalRequest.maximumCandidateCount() == 100, "retrieval request contract");
        expectUnsupported(() -> retrievalRequest.retrievalSources().add(RetrievalSource.SEARCH_INDEX));
        SearchRunV1 run = SearchContractJsonCodecV1.readRun(fixture("search-run-v1-valid.json"));
        check(run.toIntelligenceRun().runType() == IntelligenceRunType.SEARCH, "SearchRun maps to common search run");
        check(run.toIntelligenceRun().policyVersion().equals(run.rankingPolicyVersion()), "ranking policy preserved");
        check(run.reservedExposureSourceId().wireValue().equals("search_exposure_v1"), "search exposure registry ID");
        expectCode(SearchValidationErrorCode.SEARCH_RUN_STATUS_INVALID, () -> new SearchRunV1(
                SearchContractIds.SEARCH_DOMAIN, new RunRef("search-run-invalid"), IntelligenceRunStatus.FALLBACK,
                null, null, null, null, SearchSurface.EXPLORE, SearchEntityScope.POST,
                new SnapshotRef("input-1"), new SnapshotRef("candidate-1"), new SnapshotRef("output-1"),
                new SchemaVersion("search-query-normalization-v1"), new SchemaVersion("database-lexical-v1"),
                new PolicyVersion("search-ranking-policy-v1"), new FeatureDefinitionVersion("search-features-v1"),
                Instant.EPOCH, Instant.EPOCH, Instant.EPOCH, new ProducerBuildId("build-1"), ReplayClass.EXACT_REPLAY,
                new ReplayEvidenceDescriptorV1(ReplayClass.EXACT_REPLAY, true, true, true, true, false, false),
                null, null));
    }

    private static void explanationAndWireEnums() {
        SearchExplanationV1 explanation = new SearchExplanationV1(SearchContractIds.SEARCH_DOMAIN,
                "explanation:search-1", new RunRef("search-run-001"), ExplanationAudience.USER,
                List.of(SearchExplanationReason.QUERY_MATCH, SearchExplanationReason.REGION_MATCH),
                "검색어와 지역이 일치합니다.", List.of("evidence:query-match-1"), Map.of("provenance", "source_fact"),
                PrivacyClass.PUBLIC, Instant.parse("2026-07-19T12:00:02Z"));
        check(explanation.toIntelligenceExplanation().audience() == ExplanationAudience.USER,
                "search explanation maps to common evidence");
        check(explanation.toIntelligenceExplanation().reasonCodes().equals(List.of("QUERY_MATCH", "REGION_MATCH")),
                "explanation reason wire values");
        List<String> wires = List.of(SearchSurface.GLOBAL_SEARCH.wireValue(), SearchEntityType.POST.wireValue(),
                SearchFailureCode.CURSOR_INVALID.wireValue(), SearchFallbackCode.INSUFFICIENT_CANDIDATES.wireValue(),
                SearchSortType.RELEVANCE.wireValue(), SearchVisibilityState.VISIBLE.wireValue(),
                SearchEligibilityState.ELIGIBLE.wireValue());
        for (String wire : wires) check(wire.matches("[a-z][a-z0-9_]*"), "lowercase_snake_case wire: " + wire);
        check(SearchSurface.fromWire("global_search") == SearchSurface.GLOBAL_SEARCH, "enum deserialization");
        expectFailure(() -> SearchSurface.fromWire("GLOBAL_SEARCH"));
    }

    private static void isolationAndSensitiveFixtureScan() throws IOException {
        Path main = Path.of("src/main/java/com/jc/intelligence/contract/v1/search");
        if (!Files.isDirectory(main)) main = Path.of("jc-search-contracts/src/main/java/com/jc/intelligence/contract/v1/search");
        final Path sourceRoot = main;
        try (var paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    String source = Files.readString(path);
                    for (String forbidden : List.of("com.jc.recommendation", "org.springframework", "jakarta.persistence",
                            "javax.persistence", "Controller", "Repository", "Instant.now(", "System.currentTimeMillis(")) {
                        check(!source.contains(forbidden), "forbidden runtime/recommendation dependency in " + path);
                    }
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            });
        }
        Path fixtures = Path.of("src/test/resources/search");
        if (!Files.isDirectory(fixtures)) fixtures = Path.of("jc-search-contracts/src/test/resources/search");
        try (var paths = Files.walk(fixtures)) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                try {
                    String text = Files.readString(path).toLowerCase(java.util.Locale.ROOT);
                    for (String secret : List.of("access_token", "refresh_token", "api_key", "password", "authorization: bearer")) {
                        check(!text.contains(secret), "sensitive fixture token absent: " + path);
                    }
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            });
        }
    }

    private static String fixture(String name) throws IOException {
        Path path = Path.of("src/test/resources/search", name);
        if (!Files.isRegularFile(path)) path = Path.of("jc-search-contracts/src/test/resources/search", name);
        return Files.readString(path);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void expectCode(SearchValidationErrorCode code, ThrowingRunnable action) {
        assertions++;
        try {
            action.run();
        } catch (SearchContractValidationException exception) {
            if (exception.errorCode() == code) return;
            throw new AssertionError("Expected " + code + " but got " + exception.errorCode(), exception);
        } catch (Exception exception) {
            throw new AssertionError("Expected search validation code " + code, exception);
        }
        throw new AssertionError("Expected search validation code " + code);
    }

    private static void expectFailure(ThrowingRunnable action) {
        assertions++;
        try { action.run(); } catch (Exception expected) { return; }
        throw new AssertionError("Expected failure");
    }

    private static void expectUnsupported(ThrowingRunnable action) {
        assertions++;
        try { action.run(); } catch (UnsupportedOperationException expected) { return; } catch (Exception exception) {
            throw new AssertionError("Expected UnsupportedOperationException", exception);
        }
        throw new AssertionError("Expected UnsupportedOperationException");
    }

    @FunctionalInterface
    private interface ThrowingRunnable { void run() throws Exception; }
}
