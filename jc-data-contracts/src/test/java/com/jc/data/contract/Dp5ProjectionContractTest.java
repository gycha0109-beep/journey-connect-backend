package com.jc.data.contract;

import com.jc.data.contract.support.Sha256DigestV1;
import com.jc.data.contract.v1.projection.AdapterEvidenceState;
import com.jc.data.contract.v1.projection.ExperimentExposureBinding;
import com.jc.data.contract.v1.projection.ExperimentOutcomeInputProjection;
import com.jc.data.contract.v1.projection.ExperimentOutcomeProjectionEngine;
import com.jc.data.contract.v1.projection.IdentityBinding;
import com.jc.data.contract.v1.projection.ProjectionDefinition;
import com.jc.data.contract.v1.projection.ProjectionFailureCode;
import com.jc.data.contract.v1.projection.ProjectionIdentifiers;
import com.jc.data.contract.v1.projection.ProjectionPersistenceDisposition;
import com.jc.data.contract.v1.projection.ProjectionPersistenceOutcome;
import com.jc.data.contract.v1.projection.ProjectionResult;
import com.jc.data.contract.v1.projection.ProjectionSourceEvent;
import com.jc.data.contract.v1.projection.RecommendationProfileInputProjection;
import com.jc.data.contract.v1.projection.RecommendationProfileProjectionEngine;
import com.jc.data.contract.v1.projection.SourceCheckpoint;
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

public final class Dp5ProjectionContractTest {
    private static final Instant AS_OF = Instant.parse("2026-07-22T00:00:00Z");
    private static final Instant CREATED = Instant.parse("2026-07-22T00:05:00Z");
    private static final String BUILD_A = "git:1111111111111111111111111111111111111111";
    private static final String BUILD_B = "git:2222222222222222222222222222222222222222";
    private static final IdentityBinding IDENTITY = new IdentityBinding(
            "user:42", "subject:dp5-fixture", "recommendation-user-subject-binding-v1",
            "approved-binding-input", hex("identity-binding"), "journey-connect");

    private Dp5ProjectionContractTest() {
    }

    public static void main(String[] args) throws Exception {
        boolean generate = args.length == 1 && "--generate".equals(args[0]);
        ProjectionResult<RecommendationProfileInputProjection> profile = validProfile(BUILD_A, profileSources());
        ProjectionResult<ExperimentOutcomeInputProjection> outcome = validOutcome(BUILD_A, outcomeSources());
        if (generate) {
            System.out.println("fixture_id\trecord_fingerprint\tsnapshot_fingerprint\tlineage_fingerprint");
            System.out.printf(Locale.ROOT, "profile\t%s\t%s\t%s%n",
                    profile.records().getFirst().projectionRecordFingerprint(),
                    profile.snapshot().contentFingerprint(), profile.snapshot().lineageFingerprint());
            System.out.printf(Locale.ROOT, "outcome\t%s\t%s\t%s%n",
                    outcome.records().getFirst().projectionRecordFingerprint(),
                    outcome.snapshot().contentFingerprint(), outcome.snapshot().lineageFingerprint());
            return;
        }

        verifyProfile(profile);
        verifyOutcome(outcome);
        verifyGolden(profile, outcome);
        verifyDeterminism(profile, outcome);
        verifyFailures();
        verifyPersistenceDecision(profile.snapshot().contentFingerprint());
        System.out.println("DP-5 projection contract assertions: PASS");
    }

    private static void verifyProfile(ProjectionResult<RecommendationProfileInputProjection> result) {
        check(result.isSuccess(), "profile projection must succeed");
        check(result.records().size() == 3, "profile must produce 7/30/90 day records");
        check(result.records().stream().map(RecommendationProfileInputProjection::activityWindowDays).toList()
                .equals(List.of(7, 30, 90)), "profile windows");
        check(result.records().get(0).sourceEventCount() == 1L, "7 day source count");
        check(result.records().get(1).sourceEventCount() == 2L, "30 day source count");
        check(result.records().get(2).sourceEventCount() == 3L, "90 day source count");
        check(result.records().get(2).interactionCounts().equals(Map.of(
                "post_hide", 1L, "post_like", 1L, "recommendation_click", 1L)), "interaction counts");
        check(result.records().get(2).engagementSignals().equals(Map.of(
                "post_like", 1L, "recommendation_click", 1L)), "positive signals");
        check(result.records().get(2).negativeSignals().equals(Map.of("post_hide", 1L)), "negative signals");
        check(result.lineage().size() == 6, "profile record lineage completeness");
    }

    private static void verifyOutcome(ProjectionResult<ExperimentOutcomeInputProjection> result) {
        check(result.isSuccess(), "outcome projection must succeed");
        ExperimentOutcomeInputProjection record = result.records().getFirst();
        check(record.clicked() && record.liked() && !record.saved() && !record.shared(), "outcome flags");
        check(record.fallbackObserved(), "fallback observation");
        check(record.outcomeEventRefs().size() == 2, "outcome refs");
        check(record.sourceEventCount() == 3L && result.lineage().size() == 3, "outcome lineage includes exposure");
    }

    private static void verifyGolden(
            ProjectionResult<RecommendationProfileInputProjection> profile,
            ProjectionResult<ExperimentOutcomeInputProjection> outcome) throws IOException {
        Map<String, List<String>> expected = new LinkedHashMap<>();
        for (String line : Files.readAllLines(
                Path.of("../jc-data-contracts/src/test/resources/dp5-projection-golden-v1.tsv"),
                StandardCharsets.UTF_8)) {
            if (line.startsWith("fixture_id") || line.isBlank()) {
                continue;
            }
            String[] fields = line.split("\t", -1);
            expected.put(fields[0], List.of(fields[1], fields[2], fields[3]));
        }
        check(expected.get("profile").equals(List.of(
                profile.records().getFirst().projectionRecordFingerprint(),
                profile.snapshot().contentFingerprint(), profile.snapshot().lineageFingerprint())), "profile golden");
        check(expected.get("outcome").equals(List.of(
                outcome.records().getFirst().projectionRecordFingerprint(),
                outcome.snapshot().contentFingerprint(), outcome.snapshot().lineageFingerprint())), "outcome golden");
    }

    private static void verifyDeterminism(
            ProjectionResult<RecommendationProfileInputProjection> profile,
            ProjectionResult<ExperimentOutcomeInputProjection> outcome) {
        ArrayList<ProjectionSourceEvent> reorderedProfile = new ArrayList<>(profileSources());
        Collections.reverse(reorderedProfile);
        ProjectionResult<RecommendationProfileInputProjection> reordered = validProfile(BUILD_B, reorderedProfile);
        check(profile.snapshot().contentFingerprint().equals(reordered.snapshot().contentFingerprint()),
                "profile insertion/build independence");
        check(profile.snapshot().lineageFingerprint().equals(reordered.snapshot().lineageFingerprint()),
                "profile lineage order independence");

        ArrayList<ProjectionSourceEvent> reorderedOutcome = new ArrayList<>(outcomeSources());
        Collections.reverse(reorderedOutcome);
        ProjectionResult<ExperimentOutcomeInputProjection> outcome2 = validOutcome(BUILD_B, reorderedOutcome);
        check(outcome.snapshot().contentFingerprint().equals(outcome2.snapshot().contentFingerprint()),
                "outcome insertion/build independence");

        Locale originalLocale = Locale.getDefault();
        TimeZone originalZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));
            check(profile.snapshot().contentFingerprint()
                    .equals(validProfile(BUILD_A, profileSources()).snapshot().contentFingerprint()),
                    "locale/timezone independence");
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalZone);
        }

        List<ProjectionSourceEvent> withLate = new ArrayList<>(profileSources());
        withLate.add(source("event:late", "post_like", AS_OF.minusSeconds(100), AS_OF.plusSeconds(600),
                "user:42", "session:profile", "post:99", null, "post:99", List.of(), null, null,
                AdapterEvidenceState.NONE, null, null, Map.of(), "late"));
        SourceCheckpoint originalCheckpoint = profileCheckpoint(profileSources());
        ProjectionResult<RecommendationProfileInputProjection> lateResult = new RecommendationProfileProjectionEngine().project(
                ProjectionDefinition.profileV1(IDENTITY.bindingVersion()), originalCheckpoint, withLate,
                List.of(IDENTITY), AS_OF, new ProjectionIdentifiers("projection_run:profile", "snapshot:profile"),
                BUILD_A, CREATED);
        check(lateResult.isSuccess() && profile.snapshot().contentFingerprint()
                .equals(lateResult.snapshot().contentFingerprint()), "late event cannot mutate checkpoint snapshot");
    }

    private static void verifyFailures() {
        List<ProjectionSourceEvent> profileSources = profileSources();
        ProjectionResult<RecommendationProfileInputProjection> noIdentity = new RecommendationProfileProjectionEngine().project(
                ProjectionDefinition.profileV1(IDENTITY.bindingVersion()), profileCheckpoint(profileSources),
                profileSources, List.of(), AS_OF,
                new ProjectionIdentifiers("projection_run:identity", "snapshot:identity"), BUILD_A, CREATED);
        expect(noIdentity, ProjectionFailureCode.IDENTITY_BINDING_REQUIRED);

        ProjectionSourceEvent basis = profileSources.getFirst();
        ProjectionSourceEvent mismatch = copy(basis, "0".repeat(64), AdapterEvidenceState.NONE, null, null,
                basis.occurredAt(), basis.exposureRef(), basis.variantRef(), basis.identityRef());
        expect(new RecommendationProfileProjectionEngine().project(
                ProjectionDefinition.profileV1(IDENTITY.bindingVersion()), profileCheckpoint(List.of(mismatch)),
                List.of(mismatch), List.of(IDENTITY), AS_OF,
                new ProjectionIdentifiers("projection_run:fingerprint", "snapshot:fingerprint"), BUILD_A, CREATED),
                ProjectionFailureCode.SOURCE_FINGERPRINT_MISMATCH);

        ProjectionSourceEvent conflicted = copy(basis, basis.sourceFingerprint(), AdapterEvidenceState.CONFLICTED,
                null, null, basis.occurredAt(), basis.exposureRef(), basis.variantRef(), basis.identityRef());
        expect(new RecommendationProfileProjectionEngine().project(
                ProjectionDefinition.profileV1(IDENTITY.bindingVersion()), profileCheckpoint(List.of(conflicted)),
                List.of(conflicted), List.of(IDENTITY), AS_OF,
                new ProjectionIdentifiers("projection_run:conflict", "snapshot:conflict"), BUILD_A, CREATED),
                ProjectionFailureCode.ADAPTER_EVIDENCE_CONFLICTED);

        List<ProjectionSourceEvent> outcomeSources = outcomeSources();
        expect(new ExperimentOutcomeProjectionEngine().project(
                ProjectionDefinition.outcomeV1(IDENTITY.bindingVersion()), outcomeCheckpoint(outcomeSources),
                outcomeSources, IDENTITY, null, AS_OF,
                new ProjectionIdentifiers("projection_run:noexposure", "snapshot:noexposure"), BUILD_A, CREATED),
                ProjectionFailureCode.EXPOSURE_BINDING_MISSING);

        ExperimentExposureBinding general = new ExperimentExposureBinding(
                "recommendation_general_exposure", "experiment:ranking", "experiment-ranking-v1",
                "assignment:p2", "exposure:p2", "recommendation_run:p2", "user:42",
                "subject:dp5-fixture", "session:p2", "control", AS_OF.minusSeconds(3600),
                hex("exposure"), true);
        expect(new ExperimentOutcomeProjectionEngine().project(
                ProjectionDefinition.outcomeV1(IDENTITY.bindingVersion()), outcomeCheckpoint(outcomeSources),
                outcomeSources, IDENTITY, general, AS_OF,
                new ProjectionIdentifiers("projection_run:general", "snapshot:general"), BUILD_A, CREATED),
                ProjectionFailureCode.EXPOSURE_BINDING_INVALID);

        ProjectionSourceEvent outside = source("event:outside", "recommendation_click",
                AS_OF.minusSeconds(3600).plusSeconds(604_800),
                AS_OF.minusSeconds(3600).plusSeconds(604_801),
                "subject:dp5-fixture", "session:p2", "post:4", null, "post:4", List.of(),
                "exposure:p2", "control", AdapterEvidenceState.MAPPED, "adapter_output:outside",
                "recommendation-p0-mapping-policy-v1", Map.of(), "outside");
        List<ProjectionSourceEvent> invalidWindow = List.of(outcomeSources.getFirst(), outside);
        expect(new ExperimentOutcomeProjectionEngine().project(
                ProjectionDefinition.outcomeV1(IDENTITY.bindingVersion()), outcomeCheckpoint(invalidWindow),
                invalidWindow, IDENTITY, exposure(), AS_OF.plusSeconds(604_800),
                new ProjectionIdentifiers("projection_run:window", "snapshot:window"), BUILD_A, CREATED),
                ProjectionFailureCode.OUTCOME_WINDOW_VIOLATION);
    }

    private static void verifyPersistenceDecision(String fingerprint) {
        check(ProjectionPersistenceOutcome.decide(null, null, fingerprint).disposition()
                == ProjectionPersistenceDisposition.NEW, "NEW decision");
        check(ProjectionPersistenceOutcome.decide("snapshot:existing", fingerprint, fingerprint).disposition()
                == ProjectionPersistenceDisposition.DUPLICATE, "DUPLICATE decision");
        ProjectionPersistenceOutcome conflict = ProjectionPersistenceOutcome.decide(
                "snapshot:existing", fingerprint, hex("changed-snapshot"));
        check(conflict.disposition() == ProjectionPersistenceDisposition.CONFLICT
                && "PROJECTION_SNAPSHOT_CONFLICT".equals(conflict.errorCode()), "CONFLICT decision");
    }

    private static ProjectionResult<RecommendationProfileInputProjection> validProfile(
            String build, List<ProjectionSourceEvent> sources) {
        return new RecommendationProfileProjectionEngine().project(
                ProjectionDefinition.profileV1(IDENTITY.bindingVersion()), profileCheckpoint(sources), sources,
                List.of(IDENTITY), AS_OF, new ProjectionIdentifiers("projection_run:profile", "snapshot:profile"),
                build, CREATED);
    }

    private static ProjectionResult<ExperimentOutcomeInputProjection> validOutcome(
            String build, List<ProjectionSourceEvent> sources) {
        return new ExperimentOutcomeProjectionEngine().project(
                ProjectionDefinition.outcomeV1(IDENTITY.bindingVersion()), outcomeCheckpoint(sources), sources,
                IDENTITY, exposure(), AS_OF, new ProjectionIdentifiers("projection_run:outcome", "snapshot:outcome"),
                build, CREATED);
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
                exposed.plusSeconds(31), "subject:dp5-fixture", "session:p2", "post:4", null, "post:4",
                List.of(), "exposure:p2", "control", AdapterEvidenceState.MAPPED, "adapter_output:p2-click",
                "recommendation-p0-mapping-policy-v1", Map.of(), "p2-click");
        ProjectionSourceEvent like = source("event:p2-like", "post_like", exposed.plusSeconds(60),
                exposed.plusSeconds(61), "subject:dp5-fixture", "session:p2", "post:4", null, "post:4",
                List.of(), "exposure:p2", "control", AdapterEvidenceState.NONE, null, null, Map.of(), "p2-like");
        return List.of(exposure, click, like, click);
    }

    private static ExperimentExposureBinding exposure() {
        return new ExperimentExposureBinding(
                ExperimentExposureBinding.AUTHORITY, "experiment:ranking", "experiment-ranking-v1",
                "assignment:p2", "exposure:p2", "recommendation_run:p2", "user:42",
                "subject:dp5-fixture", "session:p2", "control", AS_OF.minusSeconds(3600),
                hex("exposure"), true);
    }

    private static ProjectionSourceEvent source(
            String ref, String type, Instant occurredAt, Instant ingestedAt, String identityRef,
            String sessionRef, String entityRef, String regionRef, String contentRef, List<String> tagRefs,
            String exposureRef, String variantRef, AdapterEvidenceState adapterState, String adapterRef,
            String mappingPolicy, Map<String, Object> attributes, String canonical) {
        return new ProjectionSourceEvent(
                ref, hex(canonical), "platform-event-v1", "user-behavior-event-v1", type,
                occurredAt, ingestedAt, identityRef, sessionRef, entityRef, regionRef, contentRef, tagRefs,
                exposureRef, variantRef, adapterState, adapterRef, mappingPolicy, attributes, canonical);
    }

    private static ProjectionSourceEvent copy(
            ProjectionSourceEvent source, String fingerprint, AdapterEvidenceState adapterState,
            String adapterRef, String mappingPolicy, Instant occurredAt, String exposureRef,
            String variantRef, String identityRef) {
        return new ProjectionSourceEvent(
                source.sourceEventRef(), fingerprint, source.sourceContractVersion(), source.sourceSchemaVersion(),
                source.eventType(), occurredAt, source.ingestedAt(), identityRef, source.sessionRef(),
                source.entityRef(), source.regionRef(), source.contentRef(), source.tagRefs(), exposureRef,
                variantRef, adapterState, adapterRef, mappingPolicy, source.attributes(), source.sourceCanonicalForm());
    }

    private static String hex(String text) {
        return Sha256DigestV1.lowercaseHex(text.getBytes(StandardCharsets.UTF_8));
    }

    private static void expect(ProjectionResult<?> result, ProjectionFailureCode code) {
        check(!result.isSuccess() && result.failure().code() == code, "expected failure " + code.wireValue());
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
