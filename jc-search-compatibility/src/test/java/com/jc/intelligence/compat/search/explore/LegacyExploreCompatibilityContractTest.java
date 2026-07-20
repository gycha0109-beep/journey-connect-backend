package com.jc.intelligence.compat.search.explore;

import com.jc.intelligence.compat.search.explore.v1.LegacyExploreCompatibilityAdapter;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreCompatibilityPolicy;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreCompatibilityResult;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreCompatibilityStatus;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreFixtureCase;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreFingerprintV1;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreJsonCodecV1;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreMappingFailureCode;
import com.jc.intelligence.compat.search.explore.v1.LegacyExploreWarningCode;
import com.jc.intelligence.contract.v1.search.SearchEligibilityState;
import com.jc.intelligence.contract.v1.search.SearchVisibilityState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class LegacyExploreCompatibilityContractTest {
    private static int assertions;

    private LegacyExploreCompatibilityContractTest() { }

    public static void main(String[] args) throws Exception {
        fixtureMappingContracts();
        equalityAndAuthorityContracts();
        serializationAndFingerprintContracts();
        legacyInventoryContracts();
        isolationAndWireContracts();
        System.out.println("Search compatibility contract assertions PASS: " + assertions);
    }

    private static void fixtureMappingContracts() throws IOException {
        LegacyExploreCompatibilityAdapter adapter = new LegacyExploreCompatibilityAdapter();
        List<Path> fixtures = fixturePaths();
        check(fixtures.size() == 16, "all required compatibility fixtures present");
        for (Path path : fixtures) {
            LegacyExploreFixtureCase fixture = LegacyExploreJsonCodecV1.readFixtureCase(Files.readString(path));
            LegacyExploreCompatibilityResult result = adapter.adapt(fixture.request(), fixture.page(), fixture.context());
            check(result.status() == fixture.expectedStatus(), fixture.name() + " expected status");
            check(result.searchCursorAvailable() == false, fixture.name() + " no fake cursor");
            check(result.searchRunAuthority() == false, fixture.name() + " no fake SearchRun authority");
            check(result.searchExposureAuthority() == false, fixture.name() + " no exposure authority");
            if (fixture.expectedFailureCode() == null) {
                check(result.failure() == null, fixture.name() + " success has no failure");
                check(result.items().size() == fixture.page().items().size(), fixture.name() + " item count preserved");
                check(result.pageMetadata().page() == fixture.page().page(), fixture.name() + " page preserved");
                check(result.pageMetadata().size() == fixture.page().size(), fixture.name() + " size preserved");
                check(result.pageMetadata().totalElements() == fixture.page().totalElements(), fixture.name() + " total preserved");
                for (int index = 0; index < result.items().size(); index++) {
                    var source = fixture.page().items().get(index);
                    var mapped = result.items().get(index);
                    check(mapped.sourceId().equals(Long.toString(source.id())), fixture.name() + " source ID preserved");
                    check(mapped.entityRef().value().equals("post:" + source.id()), fixture.name() + " entityRef mapped");
                    check(mapped.sourcePosition() == index + 1, fixture.name() + " order preserved");
                    check(mapped.finalPosition() == null, fixture.name() + " no fake final rank");
                    check(mapped.retrievalScore() == null, fixture.name() + " no fake retrieval score");
                    check(mapped.sourceSnapshotRef() == null, fixture.name() + " no fake snapshot");
                    check(mapped.eligibilityState() == SearchEligibilityState.UNKNOWN, fixture.name() + " eligibility unknown");
                    check(mapped.visibilityState() == SearchVisibilityState.UNKNOWN, fixture.name() + " visibility evidence not materialized");
                }
                check(result.evidence().sourceItemCount() == result.evidence().mappedItemCount(), fixture.name() + " mapping counts equal");
                check(result.evidence().rejectedItemCount() == 0, fixture.name() + " no rejected item");
            } else {
                check(result.failure() != null, fixture.name() + " typed failure exists");
                check(result.failure().failureCode() == fixture.expectedFailureCode(), fixture.name() + " expected failure code");
                check(result.items().isEmpty(), fixture.name() + " invalid payload not partial success");
            }
        }
    }

    private static void equalityAndAuthorityContracts() throws IOException {
        LegacyExploreCompatibilityAdapter adapter = new LegacyExploreCompatibilityAdapter();
        LegacyExploreFixtureCase first = load("keyword-query-first-page.json");
        LegacyExploreCompatibilityResult a = adapter.adapt(first.request(), first.page(), first.context());
        LegacyExploreCompatibilityResult b = adapter.adapt(first.request(), first.page(), first.context());
        check(a.equals(b), "mapping is deterministic");
        check(a.request().query().normalizedQuery().equals("서울 카페"), "Korean query normalization preserved");
        check(a.request().filters().isEmpty(), "no absent region filter invented");
        check(a.request().sort().sortPolicyVersion().value().equals("legacy-explore-order-v1"), "legacy order policy explicit");
        check(a.request().searchRequestAuthority() == false, "mapped request is not SearchRequest authority");
        check(a.pageMetadata().cursorAvailable() == false, "offset page not cursor wrapper");
        check(a.evidence().runtimeAuthority() == false && a.evidence().persistenceAuthority() == false
                && a.evidence().replayAuthority() == false && a.evidence().exposureAuthority() == false,
                "all compatibility authorities remain false");
        check(a.evidence().warningCodes().equals(LegacyExploreCompatibilityPolicy.BASE_WARNINGS), "warning order stable");
        check(a.evidence().warningCodes().contains(LegacyExploreWarningCode.SEARCH_CURSOR_UNAVAILABLE), "cursor warning present");
        check(a.evidence().warningCodes().contains(LegacyExploreWarningCode.RANKING_POLICY_AUTHORITY_UNAVAILABLE),
                "ranking authority warning present");
        expectFailure(() -> new com.jc.intelligence.compat.search.explore.v1.LegacyExplorePageMetadata(
                0, 20, 0, 1, 1, true, true));
        expectFailure(() -> new com.jc.intelligence.compat.search.explore.v1.LegacyExploreCompatibilityEvidence(
                com.jc.intelligence.compat.search.explore.v1.LegacyExploreContractIds.ADAPTER,
                com.jc.intelligence.compat.search.explore.v1.LegacyExploreContractIds.ENDPOINT_ID,
                "bad", "bad", com.jc.intelligence.compat.search.explore.v1.LegacyExploreContractIds.MAPPING_POLICY,
                first.context().mappedAt(), first.context().producerBuildId(), 0, 0, 0, List.of(), false, false, false, false));
        expectFailure(() -> new com.jc.intelligence.compat.search.explore.v1.LegacyExploreCompatibilityResult(
                LegacyExploreCompatibilityStatus.SUCCESS, null, List.of(), null, null, null, false, false, false));

        LegacyExploreFixtureCase sameCreated = load("multiple-items-same-created-at.json");
        LegacyExploreCompatibilityResult sameResult = adapter.adapt(sameCreated.request(), sameCreated.page(), sameCreated.context());
        check(sameResult.items().get(0).sourceId().equals("9") && sameResult.items().get(1).sourceId().equals("8"),
                "same timestamp source order preserved without reranking");

        LegacyExploreFixtureCase whitespace = load("whitespace-heavy-query.json");
        check(adapter.adapt(whitespace.request(), whitespace.page(), whitespace.context())
                .request().query().normalizedQuery().equals("seoul cafe"), "whitespace canonicalization compatible");
        LegacyExploreFixtureCase mixed = load("mixed-case-latin-query.json");
        check(adapter.adapt(mixed.request(), mixed.page(), mixed.context())
                .request().query().normalizedQuery().equals("seoul cafe"), "Locale.ROOT case canonicalization compatible");
        LegacyExploreFixtureCase region = load("unicode-korean-query.json");
        check(adapter.adapt(region.request(), region.page(), region.context()).request().filters().get(0).values().equals(List.of("서울")),
                "legacy region trim/canonicalization preserved");

        LegacyExploreCompatibilityResult nullResult = adapter.adapt(null, null, null);
        check(nullResult.status() == LegacyExploreCompatibilityStatus.INVALID_INPUT, "null safety returns typed invalid_input");
        check(nullResult.failure().failureCode() == LegacyExploreMappingFailureCode.INVALID_LEGACY_REQUEST,
                "null safety stable failure code");

        LegacyExploreFixtureCase unsupported = load("unsupported-filter.json");
        LegacyExploreCompatibilityResult unsupportedResult = adapter.adapt(unsupported.request(), unsupported.page(), unsupported.context());
        check(unsupportedResult.evidence().rejectedItemCount() == 0,
                "unsupported request does not misclassify untouched response items as rejected");
        check(!unsupportedResult.evidence().legacyRequestFingerprint().equals("0".repeat(64)),
                "failure evidence uses a real deterministic fingerprint");

        var earlyContext = new com.jc.intelligence.compat.search.explore.v1.LegacyExploreCompatibilityContext(
                first.context().requestId(), first.context().correlationId(), first.context().sessionRef(),
                first.context().referenceTime(), first.context().referenceTime().minusSeconds(1),
                first.context().producerBuildId());
        LegacyExploreCompatibilityResult early = adapter.adapt(first.request(), first.page(), earlyContext);
        check(early.status() == LegacyExploreCompatibilityStatus.INVALID_INPUT
                && early.failure().failureCode() == LegacyExploreMappingFailureCode.INVALID_TIMESTAMP,
                "mappedAt/referenceTime order is typed invalid input");
    }

    private static void serializationAndFingerprintContracts() throws IOException {
        LegacyExploreFixtureCase fixture = load("empty-query-first-page.json");
        String requestJson = LegacyExploreJsonCodecV1.writeRequest(fixture.request());
        check(LegacyExploreJsonCodecV1.readRequestJson(requestJson).equals(fixture.request()), "request JSON round trip");
        String pageJson = LegacyExploreJsonCodecV1.writePage(fixture.page());
        check(LegacyExploreJsonCodecV1.readPageJson(pageJson).equals(fixture.page()), "page JSON round trip");
        String requestFp = LegacyExploreFingerprintV1.requestFingerprint(fixture.request());
        String responseFp = LegacyExploreFingerprintV1.responseFingerprint(fixture.page());
        check(requestFp.matches("[0-9a-f]{64}"), "request fingerprint SHA-256");
        check(responseFp.matches("[0-9a-f]{64}"), "response fingerprint SHA-256");
        check(requestFp.equals(LegacyExploreFingerprintV1.requestFingerprint(fixture.request())), "request fingerprint stable");
        var reorderedUnsupported = new java.util.LinkedHashMap<String, List<String>>();
        reorderedUnsupported.put("zeta", List.of("b", "a"));
        reorderedUnsupported.put("alpha", List.of("2", "1"));
        var requestOne = new com.jc.intelligence.compat.search.explore.v1.LegacyExploreRequestView(
                null, null, 0, 20, List.of(), reorderedUnsupported);
        var reorderedAgain = new java.util.LinkedHashMap<String, List<String>>();
        reorderedAgain.put("alpha", List.of("1", "2"));
        reorderedAgain.put("zeta", List.of("a", "b"));
        var requestTwo = new com.jc.intelligence.compat.search.explore.v1.LegacyExploreRequestView(
                null, null, 0, 20, List.of(), reorderedAgain);
        check(LegacyExploreFingerprintV1.requestFingerprint(requestOne)
                .equals(LegacyExploreFingerprintV1.requestFingerprint(requestTwo)),
                "unsupported parameter fingerprint is map/value-order independent");
        check(responseFp.equals(LegacyExploreFingerprintV1.responseFingerprint(fixture.page())), "response fingerprint stable");
        check(!requestJson.contains("searchCursor") && !requestJson.contains("searchRun"), "JSON has no fake cursor/run");
        for (var status : LegacyExploreCompatibilityStatus.values())
            check(status.wireValue().matches("[a-z][a-z0-9_]*"), "status wire lowercase_snake_case");
        for (var code : LegacyExploreMappingFailureCode.values())
            check(code.wireValue().matches("[a-z][a-z0-9_]*"), "failure wire lowercase_snake_case");
        for (var code : LegacyExploreWarningCode.values())
            check(code.wireValue().matches("[a-z][a-z0-9_]*"), "warning wire lowercase_snake_case");
        expectFailure(() -> LegacyExploreJsonCodecV1.readFixtureCase(
                Files.readString(fixturePath("empty-query-first-page.json")).replace("2026-07-19T12:00:00Z", "not-an-instant")));
    }

    private static void legacyInventoryContracts() throws IOException {
        Path project = projectRoot();
        String controller = Files.readString(project.resolve("jc-backend/src/main/java/com/jc/backend/post/PostController.java"));
        String service = Files.readString(project.resolve("jc-backend/src/main/java/com/jc/backend/post/PostService.java"));
        String repository = Files.readString(project.resolve("jc-backend/src/main/java/com/jc/backend/post/JourneyPostRepository.java"));
        String dtos = Files.readString(project.resolve("jc-backend/src/main/java/com/jc/backend/post/PostDtos.java"));
        String page = Files.readString(project.resolve("jc-backend/src/main/java/com/jc/backend/common/PageResponse.java"));
        check(controller.contains("@GetMapping(\"/explore\")"), "legacy endpoint inventory");
        check(controller.contains("@RequestParam(required = false) String keyword")
                && controller.contains("@RequestParam(required = false) String region"), "legacy params inventory");
        check(controller.contains("@PageableDefault(size = 20) Pageable pageable"), "legacy page default inventory");
        check(service.contains("postService") == false && service.contains("posts.explore(blankToNull(keyword), normalizeRegionQuery(region), pageable)"),
                "legacy service mapping inventory");
        check(repository.contains("lower(p.title) like") && repository.contains("lower(p.content) like"), "legacy lexical fields inventory");
        check(repository.contains("p.moderationStatus") && repository.contains("p.visibility")
                && repository.contains("p.author.accountStatus = 'active'"), "legacy visibility predicates inventory");
        check(repository.contains("order by p.publishedAt desc, p.id desc"), "legacy deterministic repository order inventory");
        check(dtos.contains("public record Summary("), "legacy summary DTO inventory");
        check(page.contains("long totalElements") && page.contains("boolean last"), "legacy offset page metadata inventory");
    }

    private static void isolationAndWireContracts() throws IOException {
        Path project = projectRoot();
        Path sourceRoot = project.resolve("jc-search-compatibility/src/main/java");
        try (var stream = Files.walk(sourceRoot)) {
            stream.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    String source = Files.readString(path);
                    for (String forbidden : List.of("com.jc.backend", "com.jc.recommendation", "org.springframework",
                            "jakarta.persistence", "@Component", "@Service", "@Configuration", "@Bean")) {
                        check(!source.contains(forbidden), path.getFileName() + " excludes " + forbidden);
                    }
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            });
        }
        String build = Files.readString(project.resolve("jc-search-compatibility/build.gradle.kts"));
        check(build.contains("api(project(\":jc-search-contracts\"))"), "dependency direction to Search contracts");
        check(!build.contains("jc-backend") && !build.contains("jc-recommendation-core"), "no backend/recommendation dependency");
        String settings = Files.readString(project.resolve("jc-backend/settings.gradle.kts"));
        check(settings.contains("include(\":jc-search-compatibility\")"), "module registered");
        check(Files.exists(project.resolve("jc-search-compatibility/src/main/java/com/jc/intelligence/compat/search/explore/v1/LegacyExploreReadPort.java")),
                "read port defined without implementation");
    }

    private static List<Path> fixturePaths() throws IOException {
        Path root = fixtureRoot();
        try (var stream = Files.list(root)) {
            return stream.filter(path -> path.toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
        }
    }

    private static LegacyExploreFixtureCase load(String name) throws IOException {
        return LegacyExploreJsonCodecV1.readFixtureCase(Files.readString(fixturePath(name)));
    }

    private static Path fixturePath(String name) { return fixtureRoot().resolve(name); }

    private static Path fixtureRoot() {
        Path local = Path.of("src/test/resources/search-compatibility");
        if (Files.isDirectory(local)) return local;
        return projectRoot().resolve("jc-search-compatibility/src/test/resources/search-compatibility");
    }

    private static Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("jc-backend")) && Files.isDirectory(current.resolve("jc-search-compatibility"))) return current;
        if (current.getFileName() != null && current.getFileName().toString().equals("jc-search-compatibility")) return current.getParent();
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
}
