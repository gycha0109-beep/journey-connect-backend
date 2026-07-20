package com.jc.backend.intelligence.compat.recommendation;

import com.jc.backend.intelligence.compat.recommendation.RecommendationP1ProfileCompatibilityAdapterV1.RecommendationP1ProfileCompatibilityInputV1;
import com.jc.backend.intelligence.compat.recommendation.RecommendationP2AssignmentCompatibilityAdapterV1.RecommendationP2AssignmentCompatibilityInputV1;
import com.jc.backend.intelligence.compat.recommendation.RecommendationP2ExperimentExposureCompatibilityAdapterV1.RecommendationP2ExperimentExposureCompatibilityInputV1;
import com.jc.backend.recommendation.persistence.RecommendationStorageTypes.RunMode;
import com.jc.backend.recommendation.persistence.RecommendationStorageTypes.RunStatus;
import com.jc.backend.recommendation.persistence.RecommendationStorageTypes.SnapshotKind;
import com.jc.backend.recommendation.persistence.RecommendationStorageTypes.Surface;
import com.jc.intelligence.contract.support.StrictContractJsonParserV1;
import com.jc.intelligence.contract.v1.authority.ExposureSourceId;
import com.jc.intelligence.contract.v1.compatibility.CompatibilityClassification;
import com.jc.intelligence.contract.v1.replay.ReplayEvidenceDescriptorV1;
import com.jc.intelligence.contract.v1.run.IntelligenceRunStatus;
import com.jc.intelligence.contract.v1.snapshot.SnapshotRole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RecommendationCompatibilityContractAssertions {
    private int assertions;

    public int runAll() throws Exception {
        runAdapter();
        snapshotRoles();
        entityAdapter();
        classifierAndFixture();
        p1ProfileAdapter();
        p2AssignmentAdapter();
        p2ExposureAuthority();
        metricProtection();
        readOnlyArchitecture();
        fixtureSafety();
        return assertions;
    }

    private void runAdapter() {
        RecommendationRunCompatibilityInputV1 source = new RecommendationRunCompatibilityInputV1(
                "recommendation-run:001",
                "request:001",
                "correlation:001",
                RunMode.SHADOW,
                RunStatus.SUCCEEDED,
                42L,
                "session:001",
                "context:001",
                Surface.HOME,
                Instant.parse("2026-07-18T12:00:00Z"),
                Instant.parse("2026-07-18T12:00:00.001Z"),
                Instant.parse("2026-07-18T12:00:00.025Z"),
                "ranking-input-snapshot:001",
                "ranking-output-snapshot:001",
                "ranking-policy-v2",
                "feature-vocabulary-v2",
                "batch18-build-abc123",
                null,
                null,
                ReplayEvidenceDescriptorV1.deterministicRecommendationExact(true),
                null,
                ExposureSourceId.RECOMMENDATION_GENERAL_EXPOSURE_V1);

        RecommendationRunToIntelligenceRunAdapterV1 adapter =
                new RecommendationRunToIntelligenceRunAdapterV1();
        var first = adapter.adapt(source);
        var second = adapter.adapt(source);

        check(first.equals(second), "adapter repeatability");
        check(first.runId().value().equals(source.runId()), "run ID preserved");
        check(first.status() == IntelligenceRunStatus.SUCCEEDED, "status preserved");
        check(first.domainRunMode().equals("shadow"), "run mode preserved");
        check(first.surface().equals("home"), "surface preserved");
        check(first.referenceTime().equals(source.referenceTime()), "referenceTime preserved");
        check(first.policyVersion().value().equals(source.rankingPolicyVersion()), "policy preserved");
        check(first.inputSnapshotRef().value().equals(source.rankingSnapshotId()), "input snapshot preserved");
        check(first.outputSnapshotRef().value().equals(source.resultSnapshotId()), "output snapshot preserved");
        check(first.subjectRef().value().equals("user:42"), "legacy identity explicit");
        check(first.modelVersion() == null && first.promptVersion() == null, "missing model fields not guessed");
        check(first.entityRef() == null && first.experimentRef() == null, "missing domain refs not guessed");
        check(source.runStatus() == RunStatus.SUCCEEDED, "source unchanged");
    }

    private void snapshotRoles() {
        RecommendationSnapshotRoleAdapterV1 adapter = new RecommendationSnapshotRoleAdapterV1();
        var input = adapter.adapt(SnapshotKind.RANKING_INPUT_V1);
        var diversity = adapter.adapt(SnapshotKind.DIVERSITY_METADATA_V1);
        var exploration = adapter.adapt(SnapshotKind.EXPLORATION_METADATA_V1);
        var output = adapter.adapt(SnapshotKind.RANKING_RESULT_V1);
        var exposure = adapter.adapt(SnapshotKind.EXPOSURE_EVENT_V1);

        check(input.commonRole() == SnapshotRole.INPUT, "ranking input role");
        check(diversity.commonRole() == SnapshotRole.CANDIDATE && diversity.dependencyOnly(),
                "diversity candidate dependency");
        check(exploration.commonRole() == SnapshotRole.CANDIDATE && exploration.dependencyOnly(),
                "exploration candidate dependency");
        check(output.commonRole() == SnapshotRole.OUTPUT, "ranking output role");
        check(exposure.commonRole() == SnapshotRole.EXPOSURE_EVIDENCE, "exposure evidence role");
        check(exposure.exposureSourceId() == ExposureSourceId.RECOMMENDATION_GENERAL_EXPOSURE_V1,
                "general exposure source preserved");
        check(exposure.commonContractId() == null,
                "exposure evidence is not mislabeled as an output snapshot contract");
        check(input.existingSnapshotKind().equals("ranking_input_v1"), "existing kind preserved");
    }

    private void entityAdapter() {
        RecommendationEntityRefAdapterV1 adapter = new RecommendationEntityRefAdapterV1();
        check(adapter.adaptPostId(123L).value().equals("post:123"), "numeric post boundary adapter");
        check(adapter.adaptNumericCoreEntityId("456").value().equals("post:456"),
                "numeric core entity boundary adapter");
        expectIllegal(() -> adapter.adaptPostId(0L));
        expectIllegal(() -> adapter.adaptPostId(-1L));
        expectIllegal(() -> adapter.adaptNumericCoreEntityId(" 1"));
        expectIllegal(() -> adapter.adaptNumericCoreEntityId("abc"));
        expectIllegal(() -> adapter.adaptNumericCoreEntityId("0"));
    }

    private void classifierAndFixture() throws IOException {
        RecommendationCompatibilityClassifierV1 classifier =
                new RecommendationCompatibilityClassifierV1();
        check(classifier.classify("recommendation_run").classification()
                == CompatibilityClassification.ADAPTER_COMPATIBLE, "run adapter classification");
        check(classifier.classify("candidate_terminal_partition").classification()
                == CompatibilityClassification.INTENTIONALLY_DOMAIN_SPECIFIC,
                "partition domain-specific");
        check(classifier.classify("recommendation_p2_experiment_exposure").classification()
                == CompatibilityClassification.EXACT_COMPATIBLE, "P2 exposure exact authority");
        check(classifier.classify("recommendation_p0_numeric_post_id").classification()
                == CompatibilityClassification.FUTURE_VERSION_MIGRATION_REQUIRED,
                "numeric ID migration classification");

        Map<String, Object> fixture = object(fixture("recommendation-compatibility-matrix-v1.json"));
        List<Map<String, Object>> entries = objectList(fixture, "entries");
        check(entries.size() == classifier.entries().size(), "classifier fixture count");
        for (Map<String, Object> entry : entries) {
            String sourceObject = string(entry, "sourceObject");
            check(classifier.classify(sourceObject).classification().wireValue()
                            .equals(string(entry, "classification")),
                    "fixture classification " + sourceObject);
            check(classifier.classify(sourceObject).commonContractOrMeaning()
                            .equals(string(entry, "commonContractOrMeaning")),
                    "fixture common contract " + sourceObject);
        }
    }

    private void p1ProfileAdapter() {
        String hash = "a".repeat(64);
        RecommendationP1ProfileCompatibilityInputV1 source =
                new RecommendationP1ProfileCompatibilityInputV1(
                        "p1-profile:001",
                        42L,
                        "established",
                        "recommendation-profile-policy-v1",
                        "feature-vocabulary-v2",
                        Instant.parse("2026-07-18T11:00:00Z"),
                        List.of("region:KR-11", "theme:healing"),
                        hash);
        var view = new RecommendationP1ProfileCompatibilityAdapterV1().adapt(source);
        check(view.profileFingerprint().equals(hash), "P1 fingerprint preserved");
        check(view.featureVocabularyVersion().value().equals("feature-vocabulary-v2"),
                "P1 vocabulary preserved");
        check(view.authoritativeSource().equals("recommendation_p1_profile_snapshot"),
                "P1 source protected");
        check(!view.dataShadowProjectionAuthoritative(), "Data P1 shadow remains non-authoritative");
        expectUnsupported(() -> view.signalIds().add("theme:food"));
        expectIllegal(() -> new RecommendationP1ProfileCompatibilityInputV1(
                "p1-profile:002",
                42L,
                "established",
                "recommendation-profile-policy-v1",
                "feature-vocabulary-v2",
                Instant.parse("2026-07-18T11:00:00Z"),
                List.of("theme:healing", "region:KR-11"),
                hash));
    }

    private void p2AssignmentAdapter() {
        var source = new RecommendationP2AssignmentCompatibilityInputV1(
                "assignment:001",
                "recommendation-ranking-p2",
                "experiment-v1",
                "user:42",
                "treatment",
                Instant.parse("2026-07-18T10:00:00Z"));
        var view = new RecommendationP2AssignmentCompatibilityAdapterV1().adapt(source);
        check(view.subjectRef().value().equals("user:42"), "P2 legacy subject preserved");
        check(view.semanticOwner().equals("reliability"), "P2 semantic owner");
        check(view.currentPhysicalWriter().equals("recommendation_p2"), "P2 physical writer protected");
        check(!view.identityAutomaticallyMapped(), "identity mapping absent");
    }

    private void p2ExposureAuthority() {
        var source = new RecommendationP2ExperimentExposureCompatibilityInputV1(
                "p2-exposure:001",
                "assignment:001",
                "recommendation-run:001",
                42L,
                "session:001",
                "treatment",
                Instant.parse("2026-07-18T12:00:00Z"),
                "b".repeat(64));
        var adapter = new RecommendationP2ExperimentExposureCompatibilityAdapterV1();
        var first = adapter.adapt(source);
        var second = adapter.adapt(source);
        check(first.equals(second), "P2 exposure adapter deterministic");
        check(first.exposureSourceId()
                == ExposureSourceId.RECOMMENDATION_P2_EXPERIMENT_EXPOSURE_V1,
                "P2 source ID");
        check(first.authoritativeP2ExperimentDenominator(), "P2 denominator authority");
        check(!first.mergedWithGeneralOrBehaviorExposure(), "P2 exposure not merged");
        check(first.runId().value().equals(source.runId()), "P2 run binding preserved");
        check(first.subjectRef().value().equals("user:42"), "P2 subject binding preserved");
    }

    private void metricProtection() throws IOException {
        check(RecommendationP2MetricSemanticsV1.ENGAGEMENT_ATTRIBUTION_WINDOW.toDays() == 7L,
                "engagement seven-day window");
        check(RecommendationP2MetricSemanticsV1.ENGAGEMENT_EVENT_ALLOWLIST
                        .equals(Set.of("click", "like", "save", "share")),
                "engagement allowlist exact");
        for (String included : List.of("click", "like", "save", "share")) {
            check(RecommendationP2MetricSemanticsV1.isEngagementEvent(included),
                    "included engagement " + included);
        }
        for (String excluded : List.of("view", "impression", "hide", "report")) {
            check(!RecommendationP2MetricSemanticsV1.isEngagementEvent(excluded),
                    "excluded engagement " + excluded);
            check(RecommendationP2MetricSemanticsV1.isExcludedFromEngagement(excluded),
                    "explicit exclusion " + excluded);
        }
        check(RecommendationP2MetricSemanticsV1.ENGAGEMENT_EXPOSURE_SOURCE
                        == ExposureSourceId.RECOMMENDATION_P2_EXPERIMENT_EXPOSURE_V1,
                "metric P2 exposure source");
        check(RecommendationP2MetricSemanticsV1.FALLBACK_RUN_STATUS.equals("fallback"),
                "fallback run semantics");

        Map<String, Object> fixture = object(fixture("recommendation-p2-metric-protection-v1.json"));
        Map<String, Object> engagement = nested(fixture, "engagementRate");
        Map<String, Object> fallback = nested(fixture, "fallbackRate");
        check(string(engagement, "exposureSource")
                        .equals("recommendation_p2_experiment_exposure_v1"),
                "metric fixture source");
        check(number(engagement, "attributionWindowDays").longValue() == 7L,
                "metric fixture window");
        check(stringList(engagement, "includedEvents")
                        .equals(List.of("click", "like", "save", "share")),
                "metric fixture allowlist order");
        check(string(fallback, "runStatus").equals("fallback"), "metric fixture fallback");
        check(Boolean.FALSE.equals(fixture.get("sourceAggregationAllowed")),
                "metric fixture forbids source aggregation");
    }

    private void readOnlyArchitecture() throws IOException {
        Path sourceRoot = Path.of("src/main/java/com/jc/backend/intelligence/compat/recommendation");
        if (!Files.isDirectory(sourceRoot)) {
            sourceRoot = Path.of(
                    "jc-backend/src/main/java/com/jc/backend/intelligence/compat/recommendation");
        }
        final Path finalSourceRoot = sourceRoot;
        List<String> forbidden = List.of(
                "JdbcTemplate", "EntityManager", "@Repository", "@Entity",
                "insert into ", "update public.", "delete from ", "save(",
                "org.springframework", "jakarta.persistence", "java.sql.");
        try (var paths = Files.walk(finalSourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    String source = Files.readString(path).toLowerCase(java.util.Locale.ROOT);
                    for (String token : forbidden) {
                        check(!source.contains(token.toLowerCase(java.util.Locale.ROOT)),
                                "read-only adapter forbids " + token + " in " + path);
                    }
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            });
        }
    }

    private void fixtureSafety() throws IOException {
        Path fixtureRoot = Path.of("src/test/resources/intelligence/ip1");
        if (!Files.isDirectory(fixtureRoot)) {
            fixtureRoot = Path.of("jc-backend/src/test/resources/intelligence/ip1");
        }
        final Path finalFixtureRoot = fixtureRoot;
        List<String> forbidden = List.of(
                "access_token", "refresh_token", "api_key", "password",
                "raw_prompt", "secret_key", "private key");
        try (var paths = Files.walk(finalFixtureRoot)) {
            paths.filter(path -> path.toString().endsWith(".json")).forEach(path -> {
                try {
                    String value = Files.readString(path).toLowerCase(java.util.Locale.ROOT);
                    for (String token : forbidden) {
                        check(!value.contains(token), "fixture secret absent " + token);
                    }
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            });
        }
    }

    private static String fixture(String name) throws IOException {
        Path path = Path.of("src/test/resources/intelligence/ip1", name);
        if (!Files.isRegularFile(path)) {
            path = Path.of("jc-backend/src/test/resources/intelligence/ip1", name);
        }
        return Files.readString(path);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        if (value instanceof String text) {
            value = StrictContractJsonParserV1.parse(text);
        }
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> objectList(Map<String, Object> map, String field) {
        return (List<Map<String, Object>>) (List<?>) map.get(field);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nested(Map<String, Object> map, String field) {
        return (Map<String, Object>) map.get(field);
    }

    private static String string(Map<String, Object> map, String field) {
        return (String) map.get(field);
    }

    private static Number number(Map<String, Object> map, String field) {
        return (Number) map.get(field);
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Map<String, Object> map, String field) {
        return (List<String>) (List<?>) map.get(field);
    }

    private void expectIllegal(ThrowingRunnable runnable) {
        try {
            runnable.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertions++;
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private void expectUnsupported(ThrowingRunnable runnable) {
        try {
            runnable.run();
            throw new AssertionError("Expected immutable collection");
        } catch (UnsupportedOperationException expected) {
            assertions++;
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
