package com.jc.backend.recommendation.dataadoption.reconciliation;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class P2ShadowComparator {
    private static final Set<String> ENGAGEMENT = Set.of("click","like","save","share");
    private final SyntheticIdentityPolicy identityPolicy = new SyntheticIdentityPolicy();

    public Rca1Contracts.P2ComparisonResult compare(
            Rca1Contracts.ReconciliationCase fixture,
            Rca1Contracts.RecordedP2Reference reference,
            Rca1Contracts.DataCandidateP2Input candidate,
            Rca1Contracts.SyntheticIdentityBinding identity,
            Instant referenceTime) {
        if (fixture.lane()!=Rca1Contracts.Lane.P2) throw new IllegalArgumentException("P2 case required");
        List<Rca1Contracts.DimensionResult> results=new ArrayList<>();
        var identityDecision=identityPolicy.validate(identity,referenceTime);
        results.add(result(Rca1Contracts.Dimension.IDENTITY_BLOCKED,identityDecision.classification(),
                "synthetic-binding-valid",identityDecision.safeStatus(),"Synthetic identity policy.",!identityDecision.allowed()));

        String referenceAssignment=reference.experimentRef()+"|"+reference.experimentVersion()+"|"+reference.variantRef();
        String candidateAssignment=candidate.experimentRef()+"|"+candidate.experimentVersion()+"|"+candidate.variantRef();
        results.add(equal(Rca1Contracts.Dimension.ASSIGNMENT_PARITY,referenceAssignment,candidateAssignment,
                Rca1Contracts.Classification.MATCH_EXACT,"Experiment, version and variant require exact equality."));

        String referenceBinding=hashBinding(reference.subjectRef(),reference.sessionRef(),reference.runRef(),reference.exposureRef());
        String candidateBinding=hashBinding(candidate.subjectRef(),candidate.sessionRef(),candidate.runRef(),candidate.exposureRef());
        Rca1Contracts.Classification bindingMismatch = reference.subjectRef().equals(candidate.subjectRef())
                ? Rca1Contracts.Classification.RECONCILIATION_INCONCLUSIVE
                : Rca1Contracts.Classification.IDENTITY_SCHEME_MISMATCH;
        results.add(equalWithMismatch(Rca1Contracts.Dimension.SUBJECT_SESSION_RUN_PARITY,referenceBinding,candidateBinding,
                Rca1Contracts.Classification.MATCH_EXACT,bindingMismatch,"Synthetic subject/session/run/exposure binding."));

        boolean exposureAuthority=Rca1Contracts.P2_EXPOSURE_AUTHORITY.equals(candidate.exposureAuthority())
                && "p2_experiment_exposure".equals(candidate.exposureKind())
                && reference.exposureRef().equals(candidate.exposureRef());
        results.add(result(Rca1Contracts.Dimension.EXPOSURE_REFERENCE_PARITY,
                exposureAuthority?Rca1Contracts.Classification.MATCH_EXACT:Rca1Contracts.Classification.EXPOSURE_AUTHORITY_MISMATCH,
                Rca1Contracts.P2_EXPOSURE_AUTHORITY+"|"+reference.exposureRef(),
                candidate.exposureAuthority()+"|"+candidate.exposureRef(),
                "Only recommendation_p2_experiment_exposure is P2 denominator authority.",!exposureAuthority));

        boolean window=reference.outcomeWindowSeconds()==Rca1Contracts.P2_WINDOW_SECONDS
                && candidate.outcomeWindowSeconds()==Rca1Contracts.P2_WINDOW_SECONDS;
        results.add(result(Rca1Contracts.Dimension.OUTCOME_WINDOW_PARITY,
                window?Rca1Contracts.Classification.MATCH_EXACT:Rca1Contracts.Classification.OUTCOME_WINDOW_MISMATCH,
                String.valueOf(Rca1Contracts.P2_WINDOW_SECONDS),String.valueOf(candidate.outcomeWindowSeconds()),
                "Outcome window must be exactly 604800 seconds.",!window));

        boolean allowedEvents=ENGAGEMENT.containsAll(candidate.engagementEvents());
        boolean eventEquality=allowedEvents && reference.engagementEvents().equals(candidate.engagementEvents());
        results.add(result(Rca1Contracts.Dimension.ENGAGEMENT_EVENT_PARITY,
                eventEquality?Rca1Contracts.Classification.MATCH_EXACT:Rca1Contracts.Classification.PROTECTED_AUTHORITY_DIFFERENCE,
                Rca1Normalizer.collection(reference.engagementEvents()).value(),
                Rca1Normalizer.collection(candidate.engagementEvents()).value(),
                "P2 engagement is click/like/save/share only.",!eventEquality));

        boolean fallback=!candidate.fallbackObserved()
                || (reference.fallbackObserved() && reference.runRef().equals(candidate.fallbackBoundRunRef()));
        results.add(result(Rca1Contracts.Dimension.FALLBACK_BINDING_PARITY,
                fallback?Rca1Contracts.Classification.MATCH_EXACT:Rca1Contracts.Classification.FALLBACK_BINDING_MISMATCH,
                reference.runRef(),candidate.fallbackBoundRunRef(),"Fallback must be bound to the exposed Recommendation run.",!fallback));

        results.add(migration(Rca1Contracts.Dimension.STALE_UNEXPOSED_ASSIGNMENT_GAP,
                candidate.staleUnexposedAssignmentGap(),"Stale-unexposed assignment exclusion requires migration evidence."));
        results.add(migration(Rca1Contracts.Dimension.OBSERVATION_DEDUPE_GAP,
                candidate.persistedDedupeGap(),"Persisted one-observation dedupe equivalence requires migration evidence."));
        results.add(result(Rca1Contracts.Dimension.CANONICAL_DATASET_HASH_PROTECTED,
                Rca1Contracts.Classification.PROTECTED_AUTHORITY_DIFFERENCE,"protected-canonical-hash","not-read-or-recalculated",
                "Canonical dataset bytes/hash are protected.",false));
        results.add(result(Rca1Contracts.Dimension.RELEASE_EVIDENCE_PROTECTED,
                Rca1Contracts.Classification.PROTECTED_AUTHORITY_DIFFERENCE,"protected-release-evidence","not-read-or-modified",
                "Release evidence is protected.",false));
        results.add(checkpoint(reference.checkpoint(),candidate.checkpoint(),referenceTime));
        results.add(lineage(reference.lineage(),candidate.lineage()));

        Rca1Contracts.Verdict verdict=verdict(results);
        var view=new Rca1Contracts.NormalizedP2ComparisonView(
                Rca1Normalizer.scalar(referenceAssignment),Rca1Normalizer.scalar(candidateAssignment),
                Rca1Normalizer.scalar(referenceBinding),Rca1Normalizer.scalar(candidateBinding),
                Rca1Normalizer.collection(reference.engagementEvents()),Rca1Normalizer.collection(candidate.engagementEvents()));
        return new Rca1Contracts.P2ComparisonResult(fixture,view,verdict,results,
                Rca1Contracts.mismatches(fixture,results),Rca1Contracts.P2_MARKERS);
    }

    private static Rca1Contracts.DimensionResult checkpoint(Rca1Contracts.CheckpointMetadata expected,
            Rca1Contracts.CheckpointMetadata actual, Instant referenceTime) {
        String e=expected.ref()+"|"+expected.sequence()+"|"+expected.recordedAt();
        String a=actual.ref()+"|"+actual.sequence()+"|"+actual.recordedAt();
        if(!expected.ref().equals(actual.ref())||expected.sequence()!=actual.sequence())
            return result(Rca1Contracts.Dimension.SOURCE_CHECKPOINT_PARITY,Rca1Contracts.Classification.SOURCE_CHECKPOINT_MISMATCH,e,a,"Checkpoint differs.",true);
        long age=Duration.between(actual.recordedAt(),referenceTime).getSeconds();
        if(age<0||age>=actual.maxStalenessSeconds())
            return result(Rca1Contracts.Dimension.SOURCE_CHECKPOINT_PARITY,Rca1Contracts.Classification.SOURCE_STALE,e,a,"Checkpoint stale or inverted.",true);
        return result(Rca1Contracts.Dimension.SOURCE_CHECKPOINT_PARITY,Rca1Contracts.Classification.MATCH_EXACT,e,a,"Checkpoint matches and is fresh.",false);
    }

    private static Rca1Contracts.DimensionResult lineage(Rca1Contracts.LineageMetadata expected,Rca1Contracts.LineageMetadata actual) {
        boolean match=expected.fingerprint().equals(actual.fingerprint());
        return result(Rca1Contracts.Dimension.SOURCE_LINEAGE_PARITY,
                match?Rca1Contracts.Classification.MATCH_EXACT:Rca1Contracts.Classification.LINEAGE_MISMATCH,
                expected.fingerprint(),actual.fingerprint(),"Lineage fingerprint comparison.",!match);
    }

    private static Rca1Contracts.DimensionResult migration(Rca1Contracts.Dimension dimension,boolean gap,String detail) {
        return result(dimension,gap?Rca1Contracts.Classification.MIGRATION_REQUIRED:Rca1Contracts.Classification.MATCH_EXACT,
                "no-gap",gap?"migration-gap":"no-gap",detail,false);
    }

    private static Rca1Contracts.DimensionResult equal(Rca1Contracts.Dimension d,String e,String a,
            Rca1Contracts.Classification match,String detail) {
        return equalWithMismatch(d,e,a,match,Rca1Contracts.Classification.RECONCILIATION_INCONCLUSIVE,detail);
    }
    private static Rca1Contracts.DimensionResult equalWithMismatch(Rca1Contracts.Dimension d,String e,String a,
            Rca1Contracts.Classification match,Rca1Contracts.Classification mismatch,String detail) {
        boolean same=e.equals(a); return result(d,same?match:mismatch,e,a,detail,!same);
    }
    private static Rca1Contracts.DimensionResult result(Rca1Contracts.Dimension d,Rca1Contracts.Classification c,
            String e,String a,String detail,boolean blocking) {
        return new Rca1Contracts.DimensionResult(d,c,Rca1Normalizer.scalar(Rca1Normalizer.safe(e)),
                Rca1Normalizer.scalar(Rca1Normalizer.safe(a)),detail,blocking);
    }
    private static String hashBinding(String subject,String session,String run,String exposure) {
        return "sha256:"+Rca1Normalizer.hash(subject+"|"+session+"|"+run+"|"+exposure);
    }
    private static Rca1Contracts.Verdict verdict(List<Rca1Contracts.DimensionResult> results) {
        if(results.stream().anyMatch(r->(r.dimension()==Rca1Contracts.Dimension.IDENTITY_BLOCKED&&r.blocking())||r.classification()==Rca1Contracts.Classification.IDENTITY_SCHEME_MISMATCH)) return Rca1Contracts.Verdict.BLOCKED_BY_IDENTITY;
        if(results.stream().anyMatch(Rca1Contracts.DimensionResult::blocking)) return Rca1Contracts.Verdict.BLOCKED_BY_UNEXPECTED_MISMATCH;
        if(results.stream().anyMatch(r->r.classification()==Rca1Contracts.Classification.MIGRATION_REQUIRED)) return Rca1Contracts.Verdict.RECONCILED_WITH_MIGRATION_GAPS;
        return Rca1Contracts.Verdict.RECONCILED_WITH_EXPECTED_GAPS;
    }
}
