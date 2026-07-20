package com.jc.intelligence.contract;

import com.jc.intelligence.contract.support.IntelligenceContractJsonCodecV1;
import com.jc.intelligence.contract.support.StrictContractJsonParserV1;
import com.jc.intelligence.contract.v1.authority.ExposureSourceId;
import com.jc.intelligence.contract.v1.authority.ExposureSourceRegistryV1;
import com.jc.intelligence.contract.v1.identity.EntityRef;
import com.jc.intelligence.contract.v1.identity.IdentitySchemeId;
import com.jc.intelligence.contract.v1.identity.IdentitySchemeRegistryV1;
import com.jc.intelligence.contract.v1.identity.SubjectRef;
import com.jc.intelligence.contract.v1.inference.ModelInferenceRecordV1;
import com.jc.intelligence.contract.v1.replay.ReplayClass;
import com.jc.intelligence.contract.v1.replay.ReplayEvidenceDescriptorV1;
import com.jc.intelligence.contract.v1.run.IntelligenceRunStatus;
import com.jc.intelligence.contract.v1.run.IntelligenceRunV1;
import com.jc.intelligence.contract.v1.snapshot.IntelligenceCandidateSnapshotV1;
import com.jc.intelligence.contract.v1.snapshot.IntelligenceInputSnapshotV1;
import com.jc.intelligence.contract.v1.snapshot.IntelligenceOutputSnapshotV1;
import com.jc.intelligence.contract.v1.validation.ContractIdValidatorV1;
import com.jc.intelligence.contract.v1.validation.EntityRefValidatorV1;
import com.jc.intelligence.contract.v1.validation.HashValidatorV1;
import com.jc.intelligence.contract.v1.validation.IntelligenceContractValidationException;
import com.jc.intelligence.contract.v1.validation.IntelligenceValidationErrorCode;
import com.jc.intelligence.contract.v1.validation.ReplayClassValidatorV1;
import com.jc.intelligence.contract.v1.validation.SubjectRefValidatorV1;
import com.jc.intelligence.contract.v1.validation.TimeValidatorV1;
import com.jc.intelligence.contract.v1.validation.VersionValidatorV1;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class IntelligenceContractsContractTest {
    private static int assertions;

    private IntelligenceContractsContractTest() {
    }

    public static void main(String[] args) throws Exception {
        contractIdValidation();
        entityRefValidation();
        subjectRefValidation();
        timeValidation();
        runStatusValidation();
        replayValidation();
        serializationContracts();
        authorityRegistries();
        architectureIsolation();
        fixtureSafety();
        System.out.println("IP-1 intelligence contract checks passed: " + assertions);
    }

    private static void contractIdValidation() {
        check(ContractIdValidatorV1.validate("intelligence-run-v1").isValid(), "versioned ID");
        check(ContractIdValidatorV1.validate("ranking-policy-v2").isValid(), "policy ID");
        check(!ContractIdValidatorV1.validate("Intelligence-run-v1").isValid(), "uppercase rejected");
        check(!ContractIdValidatorV1.validate("intelligence run-v1").isValid(), "space rejected");
        check(!ContractIdValidatorV1.validate("latest").isValid(), "latest rejected");
        check(!ContractIdValidatorV1.validate("current").isValid(), "current rejected");
        check(!ContractIdValidatorV1.validate("default").isValid(), "default rejected");
        check(!ContractIdValidatorV1.validate("intelligence-run").isValid(), "unversioned rejected");
        check(VersionValidatorV1.validate("feature-vocabulary-v2", "featureVersion").isValid(),
                "version validator");
        check(!VersionValidatorV1.validate("feature-vocabulary", "featureVersion").isValid(),
                "unversioned semantic version rejected");
    }

    private static void entityRefValidation() {
        check(EntityRefValidatorV1.validate("post:123").isValid(), "post ref");
        check(EntityRefValidatorV1.validate("place:abc").isValid(), "opaque place ref");
        check(!EntityRefValidatorV1.validate(":123").isValid(), "empty type rejected");
        check(!EntityRefValidatorV1.validate("post:").isValid(), "empty source rejected");
        check(!EntityRefValidatorV1.validate("post:12 3").isValid(), "whitespace rejected");
        check(!EntityRefValidatorV1.validate("Post:123").isValid(), "case rejected");
        check(!EntityRefValidatorV1.validate("unregistered:123").isValid(), "unknown type rejected");
        EntityRef ref = new EntityRef("region:KR-11");
        check(ref.entityType().equals("region"), "entity type preserved");
        check(ref.sourceId().equals("KR-11"), "source ID is opaque and case-preserving");
    }

    private static void subjectRefValidation() {
        check(SubjectRefValidatorV1.validate(
                IdentitySchemeId.PLATFORM_SUBJECT_V1, "subject:opaque-123").isValid(),
                "opaque subject");
        check(SubjectRefValidatorV1.validate(
                IdentitySchemeId.LEGACY_USER_NUMERIC_V1, "user:123").isValid(),
                "legacy user");
        check(!SubjectRefValidatorV1.validate(
                IdentitySchemeId.LEGACY_USER_NUMERIC_V1, "user:0").isValid(),
                "zero rejected");
        check(!SubjectRefValidatorV1.validate(
                IdentitySchemeId.LEGACY_USER_NUMERIC_V1, "user:-1").isValid(),
                "negative rejected");
        check(!SubjectRefValidatorV1.validate(
                IdentitySchemeId.LEGACY_USER_NUMERIC_V1, "user:abc").isValid(),
                "nonnumeric rejected");
        check(!SubjectRefValidatorV1.validate(
                IdentitySchemeId.PLATFORM_SUBJECT_V1, "user:123").isValid(),
                "schemes not auto-converted");
        check(!IdentitySchemeRegistryV1.definition(
                IdentitySchemeId.PLATFORM_SUBJECT_V1).automaticConversionAllowed(),
                "platform conversion disabled");
        check(!IdentitySchemeRegistryV1.definition(
                IdentitySchemeId.LEGACY_USER_NUMERIC_V1).automaticConversionAllowed(),
                "legacy conversion disabled");
        check(new SubjectRef(IdentitySchemeId.LEGACY_USER_NUMERIC_V1, "user:42")
                .schemeId() == IdentitySchemeId.LEGACY_USER_NUMERIC_V1,
                "scheme identity retained");
    }

    private static void timeValidation() {
        Instant start = Instant.parse("2026-07-18T00:00:00Z");
        Instant end = Instant.parse("2026-07-18T00:00:01Z");
        check(TimeValidatorV1.validateRange(start, end, "startedAt", "completedAt").isValid(),
                "ordered instant");
        check(!TimeValidatorV1.validateRange(end, start, "startedAt", "completedAt").isValid(),
                "reversed instant");
        check(!TimeValidatorV1.validateRange(null, end, "startedAt", "completedAt").isValid(),
                "missing instant");
    }

    private static void runStatusValidation() throws IOException {
        IntelligenceRunV1 run = readRunFixture();
        check(run.status() == IntelligenceRunStatus.SUCCEEDED, "succeeded fixture");
        expectCode(IntelligenceValidationErrorCode.INTELLIGENCE_RUN_STATUS_INVALID, () ->
                new IntelligenceRunV1(
                        run.contractVersion(), run.runId(), run.runType(), IntelligenceRunStatus.FALLBACK,
                        run.requestId(), run.correlationId(), run.domainRunMode(), run.surface(),
                        run.entityRef(), run.subjectRef(),
                        run.inputSnapshotRef(), run.outputSnapshotRef(), run.policyVersion(),
                        run.featureDefinitionVersion(), run.modelVersion(), run.promptVersion(),
                        run.producerBuildId(), run.referenceTime(), run.startedAt(), run.completedAt(),
                        run.replayClass(), run.replayEvidence(), null, null, run.experimentRef(),
                        run.exposureSourceRef()));
        expectCode(IntelligenceValidationErrorCode.INTELLIGENCE_RUN_STATUS_INVALID, () ->
                new IntelligenceRunV1(
                        run.contractVersion(), run.runId(), run.runType(), IntelligenceRunStatus.FAILED,
                        run.requestId(), run.correlationId(), run.domainRunMode(), run.surface(),
                        run.entityRef(), run.subjectRef(),
                        run.inputSnapshotRef(), null, run.policyVersion(),
                        run.featureDefinitionVersion(), run.modelVersion(), run.promptVersion(),
                        run.producerBuildId(), run.referenceTime(), run.startedAt(), run.completedAt(),
                        run.replayClass(), run.replayEvidence(), null, null, run.experimentRef(),
                        run.exposureSourceRef()));
        expectCode(IntelligenceValidationErrorCode.INTELLIGENCE_RUN_STATUS_INVALID, () ->
                new IntelligenceRunV1(
                        run.contractVersion(), run.runId(), run.runType(), IntelligenceRunStatus.SUCCEEDED,
                        run.requestId(), run.correlationId(), run.domainRunMode(), run.surface(),
                        run.entityRef(), run.subjectRef(),
                        run.inputSnapshotRef(), run.outputSnapshotRef(), run.policyVersion(),
                        run.featureDefinitionVersion(), run.modelVersion(), run.promptVersion(),
                        run.producerBuildId(), run.referenceTime(), run.startedAt(), run.completedAt(),
                        run.replayClass(), run.replayEvidence(), null, "SHOULD_NOT_EXIST",
                        run.experimentRef(), run.exposureSourceRef()));
    }

    private static void replayValidation() {
        check(ReplayClassValidatorV1.validateConstruction(
                ReplayClass.EXACT_REPLAY, true, true, true, true, true, false).isValid(),
                "deterministic exact replay");
        check(!ReplayClassValidatorV1.validateConstruction(
                ReplayClass.EXACT_REPLAY, false, true, true, true, false, true).isValid(),
                "model inference exact replay rejected");
        check(ReplayClassValidatorV1.validateConstruction(
                ReplayClass.SEMANTIC_REPLAY, false, true, true, true, false, true).isValid(),
                "semantic replay");
        check(ReplayClassValidatorV1.validateConstruction(
                ReplayClass.EVIDENCE_REPLAY, false, true, true, true, false, true).isValid(),
                "evidence replay");
        ReplayEvidenceDescriptorV1 exact =
                ReplayEvidenceDescriptorV1.deterministicRecommendationExact(true);
        check(exact.replayClass() == ReplayClass.EXACT_REPLAY, "exact factory");
    }

    private static void serializationContracts() throws IOException {
        IntelligenceRunV1 run = readRunFixture();
        check(IntelligenceContractJsonCodecV1.readRun(
                IntelligenceContractJsonCodecV1.writeRun(run)).equals(run), "run round trip");

        IntelligenceInputSnapshotV1 input = IntelligenceContractJsonCodecV1.readInputSnapshot(
                fixture("intelligence-input-snapshot-v1-valid.json"));
        check(IntelligenceContractJsonCodecV1.readInputSnapshot(
                IntelligenceContractJsonCodecV1.writeInputSnapshot(input)).equals(input),
                "input snapshot round trip");

        IntelligenceCandidateSnapshotV1 candidates =
                IntelligenceContractJsonCodecV1.readCandidateSnapshot(
                        fixture("intelligence-candidate-snapshot-v1-valid.json"));
        check(IntelligenceContractJsonCodecV1.readCandidateSnapshot(
                IntelligenceContractJsonCodecV1.writeCandidateSnapshot(candidates)).equals(candidates),
                "candidate snapshot round trip");
        expectUnsupported(() -> candidates.candidateRefs().add(new EntityRef("post:999")));

        IntelligenceOutputSnapshotV1 output = IntelligenceContractJsonCodecV1.readOutputSnapshot(
                fixture("intelligence-output-snapshot-v1-valid.json"));
        check(IntelligenceContractJsonCodecV1.readOutputSnapshot(
                IntelligenceContractJsonCodecV1.writeOutputSnapshot(output)).equals(output),
                "output snapshot round trip");

        var feature = IntelligenceContractJsonCodecV1.readFeatureValue(
                fixture("intelligence-feature-value-v1-valid.json"));
        check(IntelligenceContractJsonCodecV1.readFeatureValue(
                IntelligenceContractJsonCodecV1.writeFeatureValue(feature)).equals(feature),
                "feature round trip");

        var explanation = IntelligenceContractJsonCodecV1.readExplanation(
                fixture("intelligence-explanation-v1-valid.json"));
        check(IntelligenceContractJsonCodecV1.readExplanation(
                IntelligenceContractJsonCodecV1.writeExplanation(explanation)).equals(explanation),
                "explanation round trip");

        ModelInferenceRecordV1 inference = IntelligenceContractJsonCodecV1.readInference(
                fixture("model-inference-record-v1-valid.json"));
        check(IntelligenceContractJsonCodecV1.readInference(
                IntelligenceContractJsonCodecV1.writeInference(inference)).equals(inference),
                "inference round trip");
        check(inference.replayClass() == ReplayClass.EVIDENCE_REPLAY, "inference not exact by default");

        String withUnknown = fixture("intelligence-run-v1-valid.json")
                .replaceFirst("\\{", "{\"futureOptionalField\":\"ignored\",");
        check(IntelligenceContractJsonCodecV1.readRun(withUnknown).equals(run),
                "unknown optional field ignored");

        String unknownRequiredEnum = fixture("intelligence-run-v1-valid.json")
                .replace("\"recommendation\"", "\"unknown_run_type\"");
        expectFailure(() -> IntelligenceContractJsonCodecV1.readRun(unknownRequiredEnum));


        String duplicateNullKey = "{\"a\":null,\"a\":1}";
        expectFailure(() -> StrictContractJsonParserV1.parse(duplicateNullKey));

        IntelligenceRunV1 modelOnlyRun = new IntelligenceRunV1(
                run.contractVersion(),
                new com.jc.intelligence.contract.v1.identity.RunRef("content-run-model-only"),
                com.jc.intelligence.contract.v1.run.IntelligenceRunType.CONTENT_ANALYSIS,
                IntelligenceRunStatus.SUCCEEDED,
                null,
                null,
                null,
                null,
                new com.jc.intelligence.contract.v1.identity.EntityRef("content:123"),
                null,
                run.inputSnapshotRef(),
                run.outputSnapshotRef(),
                null,
                null,
                new com.jc.intelligence.contract.v1.version.ModelVersion("classification-model-v1"),
                null,
                run.producerBuildId(),
                run.referenceTime(),
                run.startedAt(),
                run.completedAt(),
                ReplayClass.EVIDENCE_REPLAY,
                ReplayEvidenceDescriptorV1.modelEvidenceOnly(),
                null,
                null,
                null,
                null);
        check(modelOnlyRun.modelVersion() != null && modelOnlyRun.promptVersion() == null,
                "model version may exist without fabricated prompt version");
        check(modelOnlyRun.requestId() == null && modelOnlyRun.correlationId() == null,
                "non-request execution does not fabricate tracing IDs");

        String runJson = IntelligenceContractJsonCodecV1.writeRun(run);
        check(runJson.contains("\"runType\":\"recommendation\""), "wire enum string");
        check(!runJson.contains("\"runType\":0"), "enum ordinal absent");
        check(runJson.contains("\"referenceTime\":\"2026-07-18T12:00:00Z\""), "UTC instant");
        check(!runJson.contains("run_type"), "JSON fields camelCase");

        check(HashValidatorV1.validateSha256("a".repeat(64), "hash").isValid(), "hash accepted");
        check(!HashValidatorV1.validateSha256("A".repeat(64), "hash").isValid(), "uppercase hash rejected");
    }

    private static void authorityRegistries() throws IOException {
        var general = ExposureSourceRegistryV1.definition(
                ExposureSourceId.RECOMMENDATION_GENERAL_EXPOSURE_V1);
        var impression = ExposureSourceRegistryV1.definition(
                ExposureSourceId.RECOMMENDATION_BEHAVIOR_IMPRESSION_V1);
        var p2 = ExposureSourceRegistryV1.definition(
                ExposureSourceId.RECOMMENDATION_P2_EXPERIMENT_EXPOSURE_V1);
        var search = ExposureSourceRegistryV1.definition(ExposureSourceId.SEARCH_EXPOSURE_V1);
        check(!general.p2ExperimentDenominatorAuthority(), "general exposure excluded from P2 denominator");
        check(!impression.p2ExperimentDenominatorAuthority(), "impression excluded from P2 denominator");
        check(p2.p2ExperimentDenominatorAuthority(), "P2 source authority");
        check(!search.runtimeImplemented(), "search exposure reserved");
        check(!general.source().equals(p2.source()), "general and P2 sources distinct");
        check(!impression.source().equals(p2.source()), "impression and P2 sources distinct");

        Map<?, ?> registry = castMap(StrictContractJsonParserV1.parse(
                fixture("exposure-sources-v1.json")));
        check(Boolean.FALSE.equals(registry.get("aggregationAllowed")), "exposure aggregation forbidden");
    }

    private static void architectureIsolation() throws IOException {
        Path sourceRoot = Path.of("src/main/java");
        if (!Files.isDirectory(sourceRoot)) {
            sourceRoot = Path.of("jc-intelligence-contracts/src/main/java");
        }
        final Path finalSourceRoot = sourceRoot;
        List<String> forbidden = List.of(
                "org.springframework", "jakarta.persistence", "javax.persistence",
                "JdbcTemplate", "EntityManager", "java.sql.", "Instant.now(",
                "System.currentTimeMillis(", "System.getenv(", "System.getProperty(");
        try (var paths = Files.walk(finalSourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    String source = Files.readString(path);
                    for (String token : forbidden) {
                        check(!source.contains(token), "forbidden dependency/token " + token + " in " + path);
                    }
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            });
        }
    }

    private static void fixtureSafety() throws IOException {
        Path fixtureRoot = Path.of("src/test/resources");
        if (!Files.isDirectory(fixtureRoot)) {
            fixtureRoot = Path.of("jc-intelligence-contracts/src/test/resources");
        }
        final Path finalFixtureRoot = fixtureRoot;
        List<String> forbidden = List.of(
                "access_token", "refresh_token", "api_key", "authorization:", "raw_prompt",
                "password", "secret_key", "-----begin private key");
        try (var paths = Files.walk(finalFixtureRoot)) {
            paths.filter(path -> path.toString().endsWith(".json")).forEach(path -> {
                try {
                    String lower = Files.readString(path).toLowerCase(Locale.ROOT);
                    for (String token : forbidden) {
                        check(!lower.contains(token), "sensitive fixture token absent: " + token);
                    }
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            });
        }
    }

    private static IntelligenceRunV1 readRunFixture() throws IOException {
        return IntelligenceContractJsonCodecV1.readRun(fixture("intelligence-run-v1-valid.json"));
    }

    private static String fixture(String name) throws IOException {
        Path path = Path.of("src/test/resources", name);
        if (!Files.isRegularFile(path)) {
            path = Path.of("jc-intelligence-contracts/src/test/resources", name);
        }
        return Files.readString(path);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static void expectCode(
            IntelligenceValidationErrorCode expected,
            ThrowingRunnable runnable) {
        try {
            runnable.run();
            throw new AssertionError("Expected " + expected);
        } catch (IntelligenceContractValidationException exception) {
            check(exception.errorCode() == expected, "stable error code " + expected);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static void expectFailure(ThrowingRunnable runnable) {
        try {
            runnable.run();
            throw new AssertionError("Expected failure");
        } catch (RuntimeException expected) {
            assertions++;
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static void expectUnsupported(ThrowingRunnable runnable) {
        try {
            runnable.run();
            throw new AssertionError("Expected immutable collection");
        } catch (UnsupportedOperationException expected) {
            assertions++;
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static void check(boolean condition, String message) {
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
