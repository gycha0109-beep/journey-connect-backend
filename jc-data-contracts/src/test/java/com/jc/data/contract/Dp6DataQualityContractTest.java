package com.jc.data.contract;

import com.jc.data.contract.support.Sha256DigestV1;
import com.jc.data.contract.v1.projection.AdapterEvidenceState;
import com.jc.data.contract.v1.projection.ExperimentExposureBinding;
import com.jc.data.contract.v1.projection.ExperimentOutcomeInputProjection;
import com.jc.data.contract.v1.projection.ExperimentOutcomeProjectionEngine;
import com.jc.data.contract.v1.projection.IdentityBinding;
import com.jc.data.contract.v1.projection.ProjectionDefinition;
import com.jc.data.contract.v1.projection.ProjectionIdentifiers;
import com.jc.data.contract.v1.projection.ProjectionLineage;
import com.jc.data.contract.v1.projection.ProjectionRecord;
import com.jc.data.contract.v1.projection.ProjectionResult;
import com.jc.data.contract.v1.projection.ProjectionSnapshot;
import com.jc.data.contract.v1.projection.ProjectionSourceEvent;
import com.jc.data.contract.v1.projection.RecommendationProfileInputProjection;
import com.jc.data.contract.v1.projection.RecommendationProfileProjectionEngine;
import com.jc.data.contract.v1.projection.SourceCheckpoint;
import com.jc.data.contract.v1.quality.DataQualityCheckResult;
import com.jc.data.contract.v1.quality.DataQualityCheckStatus;
import com.jc.data.contract.v1.quality.DataQualityFailure;
import com.jc.data.contract.v1.quality.DataQualityMetric;
import com.jc.data.contract.v1.quality.DataQualityMetricCalculator;
import com.jc.data.contract.v1.quality.DataQualityMetricStatus;
import com.jc.data.contract.v1.quality.DataQualityPersistenceDisposition;
import com.jc.data.contract.v1.quality.DataQualityPersistenceOutcome;
import com.jc.data.contract.v1.quality.DataQualityPolicy;
import com.jc.data.contract.v1.quality.DataQualitySeverity;
import com.jc.data.contract.v1.quality.DataQualityValidationContext;
import com.jc.data.contract.v1.quality.DataQualityValidationDefinition;
import com.jc.data.contract.v1.quality.DataQualityValidationResult;
import com.jc.data.contract.v1.quality.DataQualityValidationScope;
import com.jc.data.contract.v1.quality.FullSnapshotQualityValidator;
import com.jc.data.contract.v1.quality.IdentityBindingEvidence;
import com.jc.data.contract.v1.quality.P2ExposureEvidence;
import com.jc.data.contract.v1.quality.SnapshotQualityStatus;
import com.jc.data.contract.v1.quality.SnapshotQualityVerdict;
import com.jc.data.contract.v1.quality.SnapshotQualityVerdictEvaluator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public final class Dp6DataQualityContractTest {
    private static final Instant AS_OF = Instant.parse("2026-07-22T00:00:00Z");
    private static final Instant CREATED = Instant.parse("2026-07-22T00:05:00Z");
    private static final String BUILD = "git:3333333333333333333333333333333333333333";
    private static final String VALIDATOR = "data-quality-validator-v1";
    private static final IdentityBinding IDENTITY = new IdentityBinding(
            "user:42", "subject:dp6-fixture", "recommendation-user-subject-binding-v1",
            "approved-binding-input", hex("identity-binding"), "journey-connect");

    private Dp6DataQualityContractTest() { }

    public static void main(String[] args) throws Exception {
        boolean generate = args.length == 1 && "--generate".equals(args[0]);
        Fixture profile = profileFixture(profileSources());
        Fixture outcome = outcomeFixture(outcomeSources());
        DataQualityValidationResult profileResult = validate("quality_run:profile", profile.context());
        DataQualityValidationResult outcomeResult = validate("quality_run:outcome", outcome.context());
        if (generate) {
            printGolden(profileResult, outcomeResult);
            return;
        }
        verifyValidated(profileResult, "profile");
        verifyValidated(outcomeResult, "outcome");
        verifyGolden(profileResult, outcomeResult);
        verifySourceFailures(profile);
        verifyProjectionAndSnapshotFailures(profile);
        verifyLineageFailures(profile);
        verifyIdentityAndExposureFailures(profile, outcome);
        verifyDeterminism(profile, outcome);
        verifyLateArrival(profile);
        verifyZeroDenominator();
        verifyMetricBoundary();
        verifyMetricRounding();
        verifyVerdictBoundaries(profileResult);
        verifyPersistenceDecision(profileResult);
        verifyFailureTaxonomy();
        System.out.println("DP-6 data quality contract assertions: PASS");
    }

    private static void verifyValidated(DataQualityValidationResult result, String fixture) {
        check(result.verdict().overallStatus() == SnapshotQualityStatus.VALIDATED,
                fixture + " must validate: " + failures(result));
        check(result.checks().stream().allMatch(check -> check.checkStatus() == DataQualityCheckStatus.PASS
                || check.checkStatus() == DataQualityCheckStatus.NOT_APPLICABLE), fixture + " check status");
        check(result.metrics().stream().noneMatch(metric -> metric.thresholdResult() == DataQualityMetricStatus.FAIL),
                fixture + " metric threshold");
        check(result.rebuildComparison().matched(), fixture + " rebuild");
    }

    private static void verifySourceFailures(Fixture fixture) {
        ArrayList<ProjectionSourceEvent> missing = new ArrayList<>(fixture.context().sourceEvents());
        missing.removeIf(event -> event.sourceEventRef().equals("event:profile-hide"));
        DataQualityValidationResult missingResult = validate("quality_run:source_missing", context(fixture, missing,
                fixture.context().projectionRecords(), fixture.context().snapshot(), fixture.context().lineage(),
                fixture.context().identityBindings(), fixture.context().exposureEvidence()));
        expectFailure(missingResult, DataQualityFailure.SOURCE_EVENT_MISSING);
        expectFailure(missingResult, DataQualityFailure.SOURCE_COUNT_MISMATCH);

        ProjectionSourceEvent first = fixture.context().sourceEvents().getFirst();
        ProjectionSourceEvent tampered = copy(first, first.sourceFingerprint(), first.adapterEvidenceState(),
                first.adapterEvidenceRef(), first.mappingPolicyVersion(), first.occurredAt().plusSeconds(1),
                first.exposureRef(), first.variantRef(), first.identityRef());
        ArrayList<ProjectionSourceEvent> timestampMismatch = new ArrayList<>(fixture.context().sourceEvents());
        timestampMismatch.set(0, tampered);
        DataQualityValidationResult timestampResult = validate("quality_run:source_timestamp", context(fixture,
                timestampMismatch, fixture.context().projectionRecords(), fixture.context().snapshot(),
                fixture.context().lineage(), fixture.context().identityBindings(), fixture.context().exposureEvidence()));
        expectFailure(timestampResult, DataQualityFailure.SOURCE_TIMESTAMP_MISMATCH);
    }

    private static void verifyProjectionAndSnapshotFailures(Fixture fixture) {
        List<ProjectionRecord> missingRecord = fixture.context().projectionRecords().subList(1,
                fixture.context().projectionRecords().size());
        DataQualityValidationResult projection = validate("quality_run:projection_missing", context(fixture,
                fixture.context().sourceEvents(), missingRecord, fixture.context().snapshot(), fixture.context().lineage(),
                fixture.context().identityBindings(), fixture.context().exposureEvidence()));
        expectFailure(projection, DataQualityFailure.PROJECTION_RECORD_MISSING);

        ProjectionSnapshot badCount = snapshotCopy(fixture.context().snapshot(),
                fixture.context().snapshot().recordCount() + 1, fixture.context().snapshot().subjectCount(),
                fixture.context().snapshot().sourceEventCount(), fixture.context().snapshot().contentFingerprint(),
                fixture.context().snapshot().lineageFingerprint(), fixture.context().snapshot().sourceCheckpointRef(),
                fixture.context().snapshot().snapshotAsOf());
        DataQualityValidationResult snapshot = validate("quality_run:snapshot_count", context(fixture,
                fixture.context().sourceEvents(), fixture.context().projectionRecords(), badCount,
                fixture.context().lineage(), fixture.context().identityBindings(), fixture.context().exposureEvidence()));
        expectFailure(snapshot, DataQualityFailure.SNAPSHOT_RECORD_COUNT_MISMATCH);

        ProjectionSnapshot badFingerprint = snapshotCopy(fixture.context().snapshot(),
                fixture.context().snapshot().recordCount(), fixture.context().snapshot().subjectCount(),
                fixture.context().snapshot().sourceEventCount(), hex("wrong-content"),
                fixture.context().snapshot().lineageFingerprint(), fixture.context().snapshot().sourceCheckpointRef(),
                fixture.context().snapshot().snapshotAsOf());
        DataQualityValidationResult fp = validate("quality_run:snapshot_fp", context(fixture,
                fixture.context().sourceEvents(), fixture.context().projectionRecords(), badFingerprint,
                fixture.context().lineage(), fixture.context().identityBindings(), fixture.context().exposureEvidence()));
        expectFailure(fp, DataQualityFailure.SNAPSHOT_CONTENT_FINGERPRINT_MISMATCH);
    }

    private static void verifyLineageFailures(Fixture fixture) {
        ArrayList<ProjectionLineage> missing = new ArrayList<>(fixture.context().lineage());
        String recordRef = fixture.context().projectionRecords().getFirst().recordRef();
        missing.removeIf(lineage -> lineage.projectionRecordRef().equals(recordRef));
        DataQualityValidationResult missingResult = validate("quality_run:lineage_missing", context(fixture,
                fixture.context().sourceEvents(), fixture.context().projectionRecords(), fixture.context().snapshot(),
                missing, fixture.context().identityBindings(), fixture.context().exposureEvidence()));
        expectFailure(missingResult, DataQualityFailure.LINEAGE_MISSING);

        ArrayList<ProjectionLineage> duplicate = new ArrayList<>(fixture.context().lineage());
        duplicate.add(duplicate.getFirst());
        DataQualityValidationResult duplicateResult = validate("quality_run:lineage_duplicate", context(fixture,
                fixture.context().sourceEvents(), fixture.context().projectionRecords(), fixture.context().snapshot(),
                duplicate, fixture.context().identityBindings(), fixture.context().exposureEvidence()));
        expectFailure(duplicateResult, DataQualityFailure.LINEAGE_DUPLICATE);
    }

    private static void verifyIdentityAndExposureFailures(Fixture profile, Fixture outcome) {
        IdentityBinding wrong = new IdentityBinding(IDENTITY.sourceIdentityRef(), IDENTITY.targetSubjectRef(),
                IDENTITY.bindingVersion(), IDENTITY.bindingSource(), hex("different-binding"), IDENTITY.bindingScope());
        IdentityBindingEvidence evidence = new IdentityBindingEvidence(wrong, IDENTITY.bindingFingerprint(),
                profile.context().checkpoint().checkpointRef(), IDENTITY.targetSubjectRef());
        DataQualityValidationResult identity = validate("quality_run:identity", context(profile,
                profile.context().sourceEvents(), profile.context().projectionRecords(), profile.context().snapshot(),
                profile.context().lineage(), List.of(evidence), profile.context().exposureEvidence()));
        expectFailure(identity, DataQualityFailure.IDENTITY_BINDING_FINGERPRINT_MISMATCH);

        IdentityBinding conflicting = new IdentityBinding(IDENTITY.sourceIdentityRef(), IDENTITY.targetSubjectRef(),
                "recommendation-user-subject-binding-v2", "second-approved-binding", hex("identity-binding-v2"),
                IDENTITY.bindingScope());
        IdentityBindingEvidence conflictEvidence = new IdentityBindingEvidence(conflicting,
                conflicting.bindingFingerprint(), profile.context().checkpoint().checkpointRef(),
                IDENTITY.targetSubjectRef());
        DataQualityValidationResult conflict = validate("quality_run:identity_conflict", context(profile,
                profile.context().sourceEvents(), profile.context().projectionRecords(), profile.context().snapshot(),
                profile.context().lineage(), List.of(profile.context().identityBindings().getFirst(), conflictEvidence),
                profile.context().exposureEvidence()));
        expectFailure(conflict, DataQualityFailure.IDENTITY_NAMESPACE_CONFLICT);

        P2ExposureEvidence general = new P2ExposureEvidence(exposure(), true, true, true);
        DataQualityValidationResult exposure = validate("quality_run:exposure", context(outcome,
                outcome.context().sourceEvents(), outcome.context().projectionRecords(), outcome.context().snapshot(),
                outcome.context().lineage(), outcome.context().identityBindings(), List.of(general)));
        expectFailure(exposure, DataQualityFailure.GENERAL_EXPOSURE_USED_AS_P2);
    }

    private static void verifyDeterminism(Fixture profile, Fixture outcome) {
        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
            Fixture reorderedProfile = reordered(profile);
            Fixture reorderedOutcome = reordered(outcome);
            DataQualityValidationResult profileResult = validate("quality_run:profile", reorderedProfile.context());
            DataQualityValidationResult outcomeResult = validate("quality_run:outcome", reorderedOutcome.context());
            DataQualityValidationResult originalProfile = validate("quality_run:profile", profile.context());
            DataQualityValidationResult originalOutcome = validate("quality_run:outcome", outcome.context());
            check(originalProfile.run().validationInputFingerprint().equals(profileResult.run().validationInputFingerprint()),
                    "input fingerprint order/locale/timezone independence");
            check(originalProfile.verdict().verdictFingerprint().equals(profileResult.verdict().verdictFingerprint()),
                    "profile verdict determinism");
            check(originalOutcome.rebuildComparison().comparisonFingerprint()
                    .equals(outcomeResult.rebuildComparison().comparisonFingerprint()), "outcome rebuild determinism");
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
    }

    private static void verifyLateArrival(Fixture fixture) {
        ProjectionSourceEvent base = fixture.context().sourceEvents().getFirst();
        ProjectionSourceEvent late = new ProjectionSourceEvent(
                "event:profile-late", hex("profile-late"), base.sourceContractVersion(), base.sourceSchemaVersion(),
                base.eventType(), fixture.context().checkpoint().eventTimeFrom().plusSeconds(30),
                fixture.context().checkpoint().ingestedAtUpperBound().plusSeconds(60), base.identityRef(),
                base.sessionRef(), "post:late", base.regionRef(), "post:late", List.of(), base.exposureRef(),
                base.variantRef(), AdapterEvidenceState.NONE, null, null, Map.of(), "profile-late");
        ArrayList<ProjectionSourceEvent> source = new ArrayList<>(fixture.context().sourceEvents());
        source.add(late);
        DataQualityValidationResult result = validate("quality_run:late", context(fixture, source,
                fixture.context().projectionRecords(), fixture.context().snapshot(), fixture.context().lineage(),
                fixture.context().identityBindings(), fixture.context().exposureEvidence()));
        check(result.lateArrivals().size() == 1, "late arrival observation");
        check(result.verdict().overallStatus() == SnapshotQualityStatus.REJECTED,
                "strict late-arrival threshold must reject the candidate quality verdict");
    }

    private static void verifyZeroDenominator() {
        DataQualityMetric metric = new DataQualityMetricCalculator().calculate("identity_binding_valid_rate", 0, 0,
                DataQualityPolicy.v1());
        check(metric.thresholdResult() == DataQualityMetricStatus.NOT_APPLICABLE, "zero denominator policy");
        check(metric.metricValue() == null, "zero denominator must not synthesize percentage");
    }

    private static void verifyMetricBoundary() {
        boolean rejected = false;
        try {
            new DataQualityMetricCalculator().calculate("source_completeness_rate", 2, 1,
                    DataQualityPolicy.v1());
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "ratio metric must reject numerator greater than denominator");
    }

    private static void verifyMetricRounding() {
        DataQualityMetric metric = new DataQualityMetricCalculator().calculate("source_completeness_rate", 1, 3,
                DataQualityPolicy.v1());
        check("0.333333333333".equals(metric.metricValue().toPlainString()),
                "ratio metric must use deterministic 12-digit HALF_UP rounding");
    }

    private static void verifyVerdictBoundaries(DataQualityValidationResult valid) {
        DataQualityPolicy policy = DataQualityPolicy.v1();
        SnapshotQualityVerdictEvaluator evaluator = new SnapshotQualityVerdictEvaluator();
        ArrayList<DataQualityCheckResult> skipped = new ArrayList<>(valid.checks());
        DataQualityCheckResult original = skipped.getFirst();
        skipped.set(0, check(original.checkCode(), original.checkScope(), DataQualityCheckStatus.SKIPPED,
                original.severity(), null, "fixture_not_executed", true));
        SnapshotQualityVerdict inconclusive = evaluator.evaluate(valid.verdict().snapshotRef(),
                "quality_run:skipped", policy, skipped, valid.metrics());
        check(inconclusive.overallStatus() == SnapshotQualityStatus.INCONCLUSIVE,
                "required skipped must be inconclusive");

        ArrayList<DataQualityCheckResult> failed = new ArrayList<>(valid.checks());
        failed.set(0, check(original.checkCode(), original.checkScope(), DataQualityCheckStatus.FAIL,
                DataQualitySeverity.BLOCKER, DataQualityFailure.QUALITY_THRESHOLD_FAILED, null, true));
        SnapshotQualityVerdict rejected = evaluator.evaluate(valid.verdict().snapshotRef(),
                "quality_run:failed", policy, failed, valid.metrics());
        check(rejected.overallStatus() == SnapshotQualityStatus.REJECTED, "blocker must reject");

        ArrayList<DataQualityCheckResult> incomplete = new ArrayList<>(valid.checks());
        incomplete.removeFirst();
        SnapshotQualityVerdict missing = evaluator.evaluate(valid.verdict().snapshotRef(),
                "quality_run:incomplete", policy, incomplete, valid.metrics());
        check(missing.overallStatus() == SnapshotQualityStatus.INCONCLUSIVE,
                "missing required check must be inconclusive");
    }

    private static void verifyPersistenceDecision(DataQualityValidationResult result) {
        String input = result.run().validationInputFingerprint();
        String verdict = result.verdict().verdictFingerprint();
        check(DataQualityPersistenceOutcome.decide("quality_run:persist", result.verdict().snapshotRef(),
                null, null, input, verdict).disposition() == DataQualityPersistenceDisposition.NEW, "NEW decision");
        check(DataQualityPersistenceOutcome.decide("quality_run:persist", result.verdict().snapshotRef(),
                input, verdict, input, verdict).disposition() == DataQualityPersistenceDisposition.DUPLICATE,
                "DUPLICATE decision");
        DataQualityPersistenceOutcome conflict = DataQualityPersistenceOutcome.decide("quality_run:persist",
                result.verdict().snapshotRef(), input, verdict, input, hex("different-verdict"));
        check(conflict.disposition() == DataQualityPersistenceDisposition.CONFLICT, "CONFLICT decision");
        check(DataQualityPersistenceOutcome.CONFLICT_CODE.equals(conflict.stableFailureCode()), "stable conflict code");
    }

    private static void verifyFailureTaxonomy() {
        for (DataQualityFailure failure : DataQualityFailure.values()) {
            check(failure.wireValue().matches("[a-z][a-z0-9_]*"), "stable snake-case failure code");
        }
    }

    private static void verifyGolden(DataQualityValidationResult profile, DataQualityValidationResult outcome)
            throws IOException {
        Map<String, List<String>> expected = new LinkedHashMap<>();
        for (String line : Files.readAllLines(
                Path.of("../jc-data-contracts/src/test/resources/dp6-quality-golden-v1.tsv"),
                StandardCharsets.UTF_8)) {
            if (line.startsWith("fixture_id") || line.isBlank()) continue;
            String[] fields = line.split("\t", -1);
            expected.put(fields[0], List.of(fields[1], fields[2], fields[3], fields[4]));
        }
        check(expected.get("profile").equals(golden(profile)), "profile quality golden");
        check(expected.get("outcome").equals(golden(outcome)), "outcome quality golden");
    }

    private static void printGolden(DataQualityValidationResult profile, DataQualityValidationResult outcome) {
        System.out.println("fixture_id\tvalidation_input_fingerprint\tverdict_fingerprint\trebuild_fingerprint\tmetric_set_fingerprint");
        printGoldenRow("profile", profile);
        printGoldenRow("outcome", outcome);
    }

    private static void printGoldenRow(String id, DataQualityValidationResult result) {
        List<String> values = golden(result);
        System.out.printf(Locale.ROOT, "%s\t%s\t%s\t%s\t%s%n", id,
                values.get(0), values.get(1), values.get(2), values.get(3));
    }

    private static List<String> golden(DataQualityValidationResult result) {
        String metrics = hex(result.metrics().stream().map(DataQualityMetric::metricFingerprint).sorted()
                .reduce("", (left, right) -> left + right));
        return List.of(result.run().validationInputFingerprint(), result.verdict().verdictFingerprint(),
                result.rebuildComparison().comparisonFingerprint(), metrics);
    }

    private static Fixture reordered(Fixture fixture) {
        ArrayList<ProjectionSourceEvent> source = new ArrayList<>(fixture.context().sourceEvents());
        ArrayList<ProjectionRecord> records = new ArrayList<>(fixture.context().projectionRecords());
        ArrayList<ProjectionLineage> lineage = new ArrayList<>(fixture.context().lineage());
        ArrayList<IdentityBindingEvidence> identity = new ArrayList<>(fixture.context().identityBindings());
        ArrayList<P2ExposureEvidence> exposure = new ArrayList<>(fixture.context().exposureEvidence());
        Collections.reverse(source);
        Collections.reverse(records);
        Collections.reverse(lineage);
        Collections.reverse(identity);
        Collections.reverse(exposure);
        return new Fixture(context(fixture, source, records, fixture.context().snapshot(), lineage, identity, exposure));
    }

    private static Fixture profileFixture(List<ProjectionSourceEvent> sources) {
        ProjectionDefinition definition = ProjectionDefinition.profileV1(IDENTITY.bindingVersion());
        SourceCheckpoint checkpoint = profileCheckpoint(sources);
        ProjectionResult<RecommendationProfileInputProjection> result = new RecommendationProfileProjectionEngine().project(
                definition, checkpoint, sources, List.of(IDENTITY), AS_OF,
                new ProjectionIdentifiers("projection_run:profile", "snapshot:profile"), BUILD, CREATED);
        check(result.isSuccess(), "profile fixture projection");
        return fixture(definition, checkpoint, sources, result, List.of());
    }

    private static Fixture outcomeFixture(List<ProjectionSourceEvent> sources) {
        ProjectionDefinition definition = ProjectionDefinition.outcomeV1(IDENTITY.bindingVersion());
        SourceCheckpoint checkpoint = outcomeCheckpoint(sources);
        ProjectionResult<ExperimentOutcomeInputProjection> result = new ExperimentOutcomeProjectionEngine().project(
                definition, checkpoint, sources, IDENTITY, exposure(), AS_OF,
                new ProjectionIdentifiers("projection_run:outcome", "snapshot:outcome"), BUILD, CREATED);
        check(result.isSuccess(), "outcome fixture projection");
        return fixture(definition, checkpoint, sources, result,
                List.of(new P2ExposureEvidence(exposure(), true, false, true)));
    }

    private static Fixture fixture(ProjectionDefinition definition, SourceCheckpoint checkpoint,
            List<ProjectionSourceEvent> sources, ProjectionResult<? extends ProjectionRecord> result,
            List<P2ExposureEvidence> exposure) {
        DataQualityValidationDefinition qualityDefinition = new DataQualityValidationDefinition(
                DataQualityValidationScope.FULL, result.snapshot().snapshotRef(), definition.projectionName(),
                definition.projectionSchemaVersion(), definition.projectionPolicyVersion(), checkpoint.checkpointRef(),
                VALIDATOR, DataQualityPolicy.VERSION, result.snapshot().snapshotAsOf());
        IdentityBindingEvidence identity = new IdentityBindingEvidence(IDENTITY, IDENTITY.bindingFingerprint(),
                checkpoint.checkpointRef(), IDENTITY.targetSubjectRef());
        List<ProjectionRecord> records = result.records().stream().map(record -> (ProjectionRecord) record).toList();
        DataQualityValidationContext context = new DataQualityValidationContext(qualityDefinition, DataQualityPolicy.v1(),
                definition, checkpoint, sources, records, result.snapshot(), result.lineage(), List.of(identity), exposure);
        return new Fixture(context);
    }

    private static DataQualityValidationContext context(Fixture fixture, List<ProjectionSourceEvent> sources,
            List<ProjectionRecord> records, ProjectionSnapshot snapshot, List<ProjectionLineage> lineage,
            List<IdentityBindingEvidence> identity, List<P2ExposureEvidence> exposure) {
        return new DataQualityValidationContext(fixture.context().definition(), fixture.context().qualityPolicy(),
                fixture.context().projectionDefinition(), fixture.context().checkpoint(), sources, records, snapshot,
                lineage, identity, exposure);
    }

    private static DataQualityValidationResult validate(String runRef, DataQualityValidationContext context) {
        return new FullSnapshotQualityValidator().validate(runRef, context);
    }

    private static SourceCheckpoint profileCheckpoint(List<ProjectionSourceEvent> sources) {
        return SourceCheckpoint.create("checkpoint:profile", "data-platform-event-v1", "platform-event-v1",
                "user-behavior-event-v1", AS_OF.minusSeconds(90L * 86_400L), AS_OF,
                AS_OF.plusSeconds(60), sources);
    }

    private static SourceCheckpoint outcomeCheckpoint(List<ProjectionSourceEvent> sources) {
        return SourceCheckpoint.create("checkpoint:outcome", "data-platform-event-v1", "platform-event-v1",
                "user-behavior-event-v1", AS_OF.minusSeconds(7200), AS_OF.plusSeconds(604_801),
                AS_OF.plusSeconds(604_900), sources);
    }

    private static List<ProjectionSourceEvent> profileSources() {
        ProjectionSourceEvent click = source("event:profile-click", "recommendation_click",
                AS_OF.minusSeconds(86_400), AS_OF.minusSeconds(86_000), "user:42", "session:profile",
                "post:1", "region:seoul", "post:1", List.of("tag:travel", "tag:food"),
                "exposure:general", null, AdapterEvidenceState.MAPPED, "adapter_output:click",
                "recommendation-p0-mapping-policy-v1", Map.of(), "profile-click");
        ProjectionSourceEvent like = source("event:profile-like", "post_like",
                AS_OF.minusSeconds(10L * 86_400L), AS_OF.minusSeconds(10L * 86_400L - 10),
                "user:42", "session:profile", "post:2", "region:busan", "post:2",
                List.of("tag:travel"), null, null, AdapterEvidenceState.NONE, null, null, Map.of(), "profile-like");
        ProjectionSourceEvent hide = source("event:profile-hide", "post_hide",
                AS_OF.minusSeconds(60L * 86_400L), AS_OF.minusSeconds(60L * 86_400L - 10),
                "user:42", "session:profile", "post:3", "region:seoul", "post:3",
                List.of("tag:night"), null, null, AdapterEvidenceState.NONE, null, null, Map.of(), "profile-hide");
        return List.of(click, like, hide, click);
    }

    private static List<ProjectionSourceEvent> outcomeSources() {
        Instant exposed = AS_OF.minusSeconds(3600);
        ProjectionSourceEvent exposure = source("event:p2-exposure", "experiment_exposure", exposed,
                exposed.plusSeconds(1), "user:42", "session:p2", "post:4", null, "post:4", List.of(),
                "exposure:p2", "control", AdapterEvidenceState.NONE, null, null, Map.of(), "p2-exposure");
        ProjectionSourceEvent click = source("event:p2-click", "recommendation_click", exposed.plusSeconds(30),
                exposed.plusSeconds(31), "subject:dp6-fixture", "session:p2", "post:4", null, "post:4",
                List.of(), "exposure:p2", "control", AdapterEvidenceState.MAPPED, "adapter_output:p2-click",
                "recommendation-p0-mapping-policy-v1", Map.of(), "p2-click");
        ProjectionSourceEvent like = source("event:p2-like", "post_like", exposed.plusSeconds(60),
                exposed.plusSeconds(61), "subject:dp6-fixture", "session:p2", "post:4", null, "post:4",
                List.of(), "exposure:p2", "control", AdapterEvidenceState.NONE, null, null, Map.of(), "p2-like");
        return List.of(exposure, click, like, click);
    }

    private static ExperimentExposureBinding exposure() {
        return new ExperimentExposureBinding(ExperimentExposureBinding.AUTHORITY, "experiment:ranking",
                "experiment-ranking-v1", "assignment:p2", "exposure:p2", "recommendation_run:p2", "user:42",
                "subject:dp6-fixture", "session:p2", "control", AS_OF.minusSeconds(3600), hex("exposure"), true);
    }

    private static ProjectionSourceEvent source(String ref, String type, Instant occurredAt, Instant ingestedAt,
            String identityRef, String sessionRef, String entityRef, String regionRef, String contentRef,
            List<String> tagRefs, String exposureRef, String variantRef, AdapterEvidenceState adapterState,
            String adapterRef, String mappingPolicy, Map<String, Object> attributes, String canonical) {
        return new ProjectionSourceEvent(ref, hex(canonical), "platform-event-v1", "user-behavior-event-v1", type,
                occurredAt, ingestedAt, identityRef, sessionRef, entityRef, regionRef, contentRef, tagRefs,
                exposureRef, variantRef, adapterState, adapterRef, mappingPolicy, attributes, canonical);
    }

    private static ProjectionSourceEvent copy(ProjectionSourceEvent source, String fingerprint,
            AdapterEvidenceState adapterState, String adapterRef, String mappingPolicy, Instant occurredAt,
            String exposureRef, String variantRef, String identityRef) {
        return new ProjectionSourceEvent(source.sourceEventRef(), fingerprint, source.sourceContractVersion(),
                source.sourceSchemaVersion(), source.eventType(), occurredAt, source.ingestedAt(), identityRef,
                source.sessionRef(), source.entityRef(), source.regionRef(), source.contentRef(), source.tagRefs(),
                exposureRef, variantRef, adapterState, adapterRef, mappingPolicy, source.attributes(),
                source.sourceCanonicalForm());
    }

    private static ProjectionSnapshot snapshotCopy(ProjectionSnapshot source, long recordCount, long subjectCount,
            long sourceCount, String contentFingerprint, String lineageFingerprint, String checkpointRef,
            Instant snapshotAsOf) {
        return new ProjectionSnapshot(source.snapshotRef(), source.projectionRunRef(), source.projectionName(),
                source.projectionSchemaVersion(), source.projectionPolicyVersion(), checkpointRef, snapshotAsOf,
                recordCount, subjectCount, sourceCount, contentFingerprint, lineageFingerprint,
                source.snapshotStatus(), source.createdAt(), source.retentionClass(), source.retentionPolicyVersion(),
                source.expiresAt());
    }

    private static DataQualityCheckResult check(String code, DataQualityValidationScope scope,
            DataQualityCheckStatus status, DataQualitySeverity severity, DataQualityFailure failure,
            String reason, boolean required) {
        String expected = status == DataQualityCheckStatus.SKIPPED ? "required" : "expected";
        String observed = status == DataQualityCheckStatus.SKIPPED ? "not_executed" : "failed";
        String difference = status == DataQualityCheckStatus.SKIPPED ? "unknown" : "1";
        String fingerprint = com.jc.data.contract.v1.quality.DataQualityFingerprints.check(code, scope, expected,
                observed, difference, severity, status, failure, reason, required);
        return new DataQualityCheckResult(code, scope, expected, observed, difference, severity, status, failure,
                reason, required, fingerprint);
    }

    private static void expectFailure(DataQualityValidationResult result, DataQualityFailure failure) {
        check(result.checks().stream().anyMatch(check -> check.failureCode() == failure),
                "expected quality failure " + failure.wireValue() + ": " + failures(result));
        check(result.verdict().overallStatus() == SnapshotQualityStatus.REJECTED,
                "quality failure must reject " + failure.wireValue());
    }

    private static String failures(DataQualityValidationResult result) {
        return result.checks().stream().filter(check -> check.failureCode() != null)
                .map(check -> check.failureCode().wireValue()).toList().toString();
    }

    private static String hex(String text) {
        return Sha256DigestV1.lowercaseHex(text.getBytes(StandardCharsets.UTF_8));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record Fixture(DataQualityValidationContext context) { }
}
