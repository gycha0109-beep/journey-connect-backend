package com.jc.backend.recommendation.dataadoption.reconciliation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class Rca1Contracts {
    public static final String RECONCILIATION_ID = "recommendation-shadow-reconciliation-v1";
    public static final String EVIDENCE_ID = "recommendation-shadow-reconciliation-evidence-v1";
    public static final String FIXTURE_ID = "recommendation-shadow-reconciliation-fixture-v1";
    public static final String VERSION = "v1";
    public static final String VERIFIER_VERSION = "rca1-offline-verifier-v1";
    public static final String MODEL = "MODEL_A_OFFLINE_DETERMINISTIC_RECONCILIATION";
    public static final String IDENTITY_MODE = "SYNTHETIC_ONLY";
    public static final String PURPOSE = "RCA1_OFFLINE_RECONCILIATION_ONLY";
    public static final String CALLER = "rca1-fixture-runner";
    public static final String P2_EXPOSURE_AUTHORITY = "recommendation_p2_experiment_exposure";
    public static final long P2_WINDOW_SECONDS = 604_800L;
    public static final Set<String> P2_MARKERS = Set.of(
            "P2_SHADOW_RECONCILIATION_ONLY",
            "CURRENT_P2_AUTHORITY_UNCHANGED",
            "NO_AUTHORITY_TRANSFER");

    private Rca1Contracts() {}

    public enum Lane { P1, P2 }
    public enum Role { BASELINE, EXPECTED_NEGATIVE }
    public enum Dimension {
        SOURCE_CHECKPOINT_PARITY, SOURCE_LINEAGE_PARITY,
        EXACT_FIELD_PARITY, DERIVED_VALUE_PARITY, AGGREGATE_WINDOW_PARITY,
        ORDERING_NOT_COMPARABLE, EVENT_GRAIN_MISSING, EXPLICIT_PREFERENCE_MISSING,
        TRANSFORM_POLICY_MISSING, FINGERPRINT_SEMANTICS_PROTECTED,
        EXPOSURE_REFERENCE_PARITY, ASSIGNMENT_PARITY, SUBJECT_SESSION_RUN_PARITY,
        OUTCOME_WINDOW_PARITY, ENGAGEMENT_EVENT_PARITY, FALLBACK_BINDING_PARITY,
        STALE_UNEXPOSED_ASSIGNMENT_GAP, OBSERVATION_DEDUPE_GAP,
        CANONICAL_DATASET_HASH_PROTECTED, RELEASE_EVIDENCE_PROTECTED,
        IDENTITY_BLOCKED
    }
    public enum Classification {
        MATCH_EXACT, MATCH_DERIVED, EXPECTED_SEMANTIC_GAP, MIGRATION_REQUIRED,
        IDENTITY_MAPPING_REQUIRED, IDENTITY_SCHEME_MISMATCH,
        SOURCE_CHECKPOINT_MISMATCH, SOURCE_STALE, LINEAGE_MISMATCH,
        EXPOSURE_AUTHORITY_MISMATCH, OUTCOME_WINDOW_MISMATCH,
        FALLBACK_BINDING_MISMATCH, PROTECTED_AUTHORITY_DIFFERENCE,
        RECONCILIATION_INCONCLUSIVE
    }
    public enum Verdict {
        RECONCILED_WITH_EXPECTED_GAPS,
        RECONCILED_WITH_MIGRATION_GAPS,
        BLOCKED_BY_UNEXPECTED_MISMATCH,
        BLOCKED_BY_IDENTITY,
        INCONCLUSIVE
    }
    public enum IdentityStatus {
        VALID, ABSENT, INVALID, EXPIRED, DELETED, MISMATCHED,
        UNAUTHORIZED_PURPOSE, UNAUTHORIZED_CALLER
    }

    public record NormalizedValue(String value) {
        public NormalizedValue { value = text(value, "value"); }
    }
    public record CheckpointMetadata(String ref, long sequence, Instant recordedAt, long maxStalenessSeconds) {
        public CheckpointMetadata {
            ref = text(ref, "ref"); Objects.requireNonNull(recordedAt, "recordedAt");
            if (sequence < 0 || maxStalenessSeconds < 0) throw new IllegalArgumentException("invalid checkpoint");
        }
    }
    public record LineageMetadata(String fingerprint) {
        public LineageMetadata {
            fingerprint = text(fingerprint, "fingerprint");
            if (!fingerprint.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("invalid fingerprint");
        }
    }
    public record SyntheticIdentityBinding(String subjectRef, String userRef, IdentityStatus status,
            String purpose, String caller, Instant validUntil, boolean deleted) {
        public SyntheticIdentityBinding {
            subjectRef = text(subjectRef, "subjectRef"); userRef = text(userRef, "userRef");
            Objects.requireNonNull(status, "status"); purpose = text(purpose, "purpose");
            caller = text(caller, "caller"); Objects.requireNonNull(validUntil, "validUntil");
        }
    }
    public record IdentityDecision(boolean allowed, Classification classification, String safeStatus) {
        public IdentityDecision { Objects.requireNonNull(classification, "classification"); safeStatus = text(safeStatus, "safeStatus"); }
    }
    public record DimensionResult(Dimension dimension, Classification classification,
            NormalizedValue expected, NormalizedValue actual, String detail, boolean blocking) {
        public DimensionResult {
            Objects.requireNonNull(dimension, "dimension"); Objects.requireNonNull(classification, "classification");
            Objects.requireNonNull(expected, "expected"); Objects.requireNonNull(actual, "actual");
            detail = text(detail, "detail");
        }
        public boolean match() { return classification == Classification.MATCH_EXACT || classification == Classification.MATCH_DERIVED; }
    }
    public record MismatchItem(Lane lane, String scenario, Dimension dimension,
            Classification classification, String detail, boolean blocking) {
        public MismatchItem {
            Objects.requireNonNull(lane, "lane"); scenario = text(scenario, "scenario");
            Objects.requireNonNull(dimension, "dimension"); Objects.requireNonNull(classification, "classification");
            detail = text(detail, "detail");
        }
    }
    public record ReconciliationCase(Lane lane, String scenario, Role role, Verdict expectedVerdict,
            Dimension targetDimension, Classification expectedPrimaryClassification, Map<String,String> fields) {
        public ReconciliationCase {
            Objects.requireNonNull(lane, "lane"); scenario = text(scenario, "scenario");
            Objects.requireNonNull(role, "role"); Objects.requireNonNull(expectedVerdict, "expectedVerdict");
            Objects.requireNonNull(targetDimension, "targetDimension");
            Objects.requireNonNull(expectedPrimaryClassification, "expectedPrimaryClassification");
            fields = strings(fields);
        }
        public String field(String key) { return fields.getOrDefault(key, ""); }
        public boolean flag(String key) { return Boolean.parseBoolean(field(key)); }
        public long number(String key) { return Long.parseLong(field(key)); }
        public Instant instant(String key) { return Instant.parse(field(key)); }
    }

    public record RecordedP1Reference(Map<String,String> exactFields, Map<String,Long> counts,
            Map<Integer,Long> windows, CheckpointMetadata checkpoint, LineageMetadata lineage, String fingerprint) {
        public RecordedP1Reference {
            exactFields = strings(exactFields); counts = longs(counts); windows = windowMap(windows);
            Objects.requireNonNull(checkpoint, "checkpoint"); Objects.requireNonNull(lineage, "lineage");
            fingerprint = text(fingerprint, "fingerprint");
        }
    }
    public record DataCandidateP1Input(Map<String,String> exactFields, Map<String,Long> counts,
            Map<Integer,Long> windows, CheckpointMetadata checkpoint, LineageMetadata lineage,
            boolean orderingComparable, boolean eventGrainAvailable, boolean explicitPreferencesAvailable,
            boolean transformPolicyAvailable, String fingerprint) {
        public DataCandidateP1Input {
            exactFields = strings(exactFields); counts = longs(counts); windows = windowMap(windows);
            Objects.requireNonNull(checkpoint, "checkpoint"); Objects.requireNonNull(lineage, "lineage");
            fingerprint = text(fingerprint, "fingerprint");
        }
    }
    public record NormalizedP1ComparisonView(NormalizedValue referenceExact, NormalizedValue candidateExact,
            NormalizedValue referenceDerived, NormalizedValue candidateDerived,
            NormalizedValue referenceWindows, NormalizedValue candidateWindows) {
        public NormalizedP1ComparisonView { Objects.requireNonNull(referenceExact); Objects.requireNonNull(candidateExact); Objects.requireNonNull(referenceDerived); Objects.requireNonNull(candidateDerived); Objects.requireNonNull(referenceWindows); Objects.requireNonNull(candidateWindows); }
    }
    public record P1ComparisonResult(ReconciliationCase reconciliationCase, NormalizedP1ComparisonView normalizedView,
            Verdict verdict, List<DimensionResult> dimensions, List<MismatchItem> mismatchInventory) {
        public P1ComparisonResult {
            Objects.requireNonNull(reconciliationCase); Objects.requireNonNull(normalizedView); Objects.requireNonNull(verdict);
            dimensions = List.copyOf(dimensions); mismatchInventory = List.copyOf(mismatchInventory);
        }
    }

    public record RecordedP2Reference(String experimentRef, String experimentVersion, String variantRef,
            String exposureRef, String subjectRef, String sessionRef, String runRef,
            long outcomeWindowSeconds, Set<String> engagementEvents, boolean fallbackObserved,
            CheckpointMetadata checkpoint, LineageMetadata lineage) {
        public RecordedP2Reference {
            experimentRef=text(experimentRef,"experimentRef"); experimentVersion=text(experimentVersion,"experimentVersion");
            variantRef=text(variantRef,"variantRef"); exposureRef=text(exposureRef,"exposureRef");
            subjectRef=text(subjectRef,"subjectRef"); sessionRef=text(sessionRef,"sessionRef"); runRef=text(runRef,"runRef");
            engagementEvents=stringsSet(engagementEvents); Objects.requireNonNull(checkpoint); Objects.requireNonNull(lineage);
        }
        public String observationKey() { return experimentRef + "|" + experimentVersion + "|" + subjectRef; }
    }
    public record DataCandidateP2Input(String experimentRef, String experimentVersion, String variantRef,
            String exposureAuthority, String exposureKind, String exposureRef, String subjectRef,
            String sessionRef, String runRef, long outcomeWindowSeconds, Set<String> engagementEvents,
            boolean fallbackObserved, String fallbackBoundRunRef, boolean staleUnexposedAssignmentGap,
            boolean persistedDedupeGap, CheckpointMetadata checkpoint, LineageMetadata lineage) {
        public DataCandidateP2Input {
            experimentRef=text(experimentRef,"experimentRef"); experimentVersion=text(experimentVersion,"experimentVersion");
            variantRef=text(variantRef,"variantRef"); exposureAuthority=text(exposureAuthority,"exposureAuthority");
            exposureKind=text(exposureKind,"exposureKind"); exposureRef=text(exposureRef,"exposureRef");
            subjectRef=text(subjectRef,"subjectRef"); sessionRef=text(sessionRef,"sessionRef"); runRef=text(runRef,"runRef");
            engagementEvents=stringsSet(engagementEvents); fallbackBoundRunRef=text(fallbackBoundRunRef,"fallbackBoundRunRef");
            Objects.requireNonNull(checkpoint); Objects.requireNonNull(lineage);
        }
        public String observationKey() { return experimentRef + "|" + experimentVersion + "|" + subjectRef; }
    }
    public record NormalizedP2ComparisonView(NormalizedValue referenceAssignment, NormalizedValue candidateAssignment,
            NormalizedValue referenceBinding, NormalizedValue candidateBinding,
            NormalizedValue referenceEngagement, NormalizedValue candidateEngagement) {
        public NormalizedP2ComparisonView { Objects.requireNonNull(referenceAssignment); Objects.requireNonNull(candidateAssignment); Objects.requireNonNull(referenceBinding); Objects.requireNonNull(candidateBinding); Objects.requireNonNull(referenceEngagement); Objects.requireNonNull(candidateEngagement); }
    }
    public record P2ComparisonResult(ReconciliationCase reconciliationCase, NormalizedP2ComparisonView normalizedView,
            Verdict verdict, List<DimensionResult> dimensions, List<MismatchItem> mismatchInventory, Set<String> authorityMarkers) {
        public P2ComparisonResult {
            Objects.requireNonNull(reconciliationCase); Objects.requireNonNull(normalizedView); Objects.requireNonNull(verdict);
            dimensions=List.copyOf(dimensions); mismatchInventory=List.copyOf(mismatchInventory); authorityMarkers=stringsSet(authorityMarkers);
        }
    }

    public record RedactedEvidenceRecord(String hashedCaseId, Lane lane, String contractId, String contractVersion,
            Dimension comparisonDimension, Classification classification, String normalizedExpected,
            String normalizedActual, String checkpoint, String lineageFingerprint, Instant evidenceTimestamp,
            String verifierVersion, String testedSha) {
        public RedactedEvidenceRecord {
            hashedCaseId=text(hashedCaseId,"hashedCaseId"); Objects.requireNonNull(lane); contractId=text(contractId,"contractId");
            contractVersion=text(contractVersion,"contractVersion"); Objects.requireNonNull(comparisonDimension);
            Objects.requireNonNull(classification); normalizedExpected=text(normalizedExpected,"normalizedExpected");
            normalizedActual=text(normalizedActual,"normalizedActual"); checkpoint=text(checkpoint,"checkpoint");
            lineageFingerprint=text(lineageFingerprint,"lineageFingerprint"); Objects.requireNonNull(evidenceTimestamp);
            verifierVersion=text(verifierVersion,"verifierVersion"); testedSha=text(testedSha,"testedSha");
        }
    }
    public record ReconciliationCounters(long reconciliationCaseCount, long p1ExactMatchCount,
            long p1ExpectedGapCount, long p1UnexpectedMismatchCount, long p2ExactMatchCount,
            long p2MigrationRequiredCount, long p2AuthorityMismatchCount, long identityBlockedCount,
            long checkpointMismatchCount, long lineageMismatchCount, long inconclusiveCount) {}

    public static List<MismatchItem> mismatches(ReconciliationCase c, List<DimensionResult> results) {
        ArrayList<MismatchItem> out = new ArrayList<>();
        for (DimensionResult r : results) if (!r.match()) out.add(new MismatchItem(c.lane(),c.scenario(),r.dimension(),r.classification(),r.detail(),r.blocking()));
        return List.copyOf(out);
    }
    private static Map<String,String> strings(Map<String,String> source) {
        Objects.requireNonNull(source); TreeMap<String,String> out=new TreeMap<>(); source.forEach((k,v)->out.put(text(k,"key"),v==null?"":v)); return Collections.unmodifiableMap(out);
    }
    private static Map<String,Long> longs(Map<String,Long> source) {
        Objects.requireNonNull(source); TreeMap<String,Long> out=new TreeMap<>(); source.forEach((k,v)->{ if(v==null||v<0) throw new IllegalArgumentException("invalid count"); out.put(text(k,"key"),v); }); return Collections.unmodifiableMap(out);
    }
    private static Map<Integer,Long> windowMap(Map<Integer,Long> source) {
        Objects.requireNonNull(source); TreeMap<Integer,Long> out=new TreeMap<>(); source.forEach((k,v)->{ if(k==null||!Set.of(7,30,90).contains(k)||v==null||v<0) throw new IllegalArgumentException("invalid window"); out.put(k,v); }); if(!out.keySet().equals(Set.of(7,30,90))) throw new IllegalArgumentException("7/30/90 required"); return Collections.unmodifiableMap(out);
    }
    private static Set<String> stringsSet(Set<String> source) {
        Objects.requireNonNull(source); TreeSet<String> out=new TreeSet<>(); for(String s:source) if(s!=null&&!s.isBlank()) out.add(s); return Collections.unmodifiableSet(out);
    }
    static String text(String value,String field) { if(value==null||value.isBlank()) throw new IllegalArgumentException(field+" required"); return value; }
}
