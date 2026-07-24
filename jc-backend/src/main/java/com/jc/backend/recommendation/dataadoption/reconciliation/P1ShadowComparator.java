package com.jc.backend.recommendation.dataadoption.reconciliation;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class P1ShadowComparator {
    private final SyntheticIdentityPolicy identityPolicy = new SyntheticIdentityPolicy();

    public Rca1Contracts.P1ComparisonResult compare(
            Rca1Contracts.ReconciliationCase fixture,
            Rca1Contracts.RecordedP1Reference reference,
            Rca1Contracts.DataCandidateP1Input candidate,
            Rca1Contracts.SyntheticIdentityBinding identity,
            Instant referenceTime) {
        if (fixture.lane() != Rca1Contracts.Lane.P1) throw new IllegalArgumentException("P1 case required");
        List<Rca1Contracts.DimensionResult> results = new ArrayList<>();
        Rca1Contracts.IdentityDecision identityDecision = identityPolicy.validate(identity, referenceTime);
        results.add(result(Rca1Contracts.Dimension.IDENTITY_BLOCKED, identityDecision.classification(),
                "synthetic-binding-valid", identityDecision.safeStatus(), "Synthetic identity policy.", !identityDecision.allowed()));

        var referenceExact = Rca1Normalizer.map(reference.exactFields());
        var candidateExact = Rca1Normalizer.map(candidate.exactFields());
        results.add(equal(Rca1Contracts.Dimension.EXACT_FIELD_PARITY, referenceExact, candidateExact,
                Rca1Contracts.Classification.MATCH_EXACT, "Shared fields require zero mismatch."));

        var referenceDerived = Rca1Normalizer.map(reference.counts());
        var candidateDerived = Rca1Normalizer.map(candidate.counts());
        results.add(equal(Rca1Contracts.Dimension.DERIVED_VALUE_PARITY, referenceDerived, candidateDerived,
                Rca1Contracts.Classification.MATCH_DERIVED, "Derived counts require zero mismatch."));

        var referenceWindows = Rca1Normalizer.map(reference.windows());
        var candidateWindows = Rca1Normalizer.map(candidate.windows());
        results.add(equal(Rca1Contracts.Dimension.AGGREGATE_WINDOW_PARITY, referenceWindows, candidateWindows,
                Rca1Contracts.Classification.MATCH_EXACT, "All 7/30/90 day windows are compared explicitly."));

        results.add(checkpoint(reference.checkpoint(), candidate.checkpoint(), referenceTime));
        results.add(lineage(reference.lineage(), candidate.lineage()));
        results.add(gap(Rca1Contracts.Dimension.ORDERING_NOT_COMPARABLE, candidate.orderingComparable(),
                "Aggregate projection has no authoritative event ordering."));
        results.add(gap(Rca1Contracts.Dimension.EVENT_GRAIN_MISSING, candidate.eventGrainAvailable(),
                "Aggregate projection cannot fabricate BehaviorProfileEvent rows."));
        results.add(gap(Rca1Contracts.Dimension.EXPLICIT_PREFERENCE_MISSING, candidate.explicitPreferencesAvailable(),
                "Explicit recommendation preferences are absent from the candidate aggregate."));
        results.add(gap(Rca1Contracts.Dimension.TRANSFORM_POLICY_MISSING, candidate.transformPolicyAvailable(),
                "Feature vocabulary, decay and saturation policy are not candidate authority."));
        results.add(result(Rca1Contracts.Dimension.FINGERPRINT_SEMANTICS_PROTECTED,
                Rca1Contracts.Classification.PROTECTED_AUTHORITY_DIFFERENCE,
                "protected-profile-fingerprint", "candidate-projection-fingerprint",
                "Current P1 snapshot fingerprint semantics remain protected.", false));

        Rca1Contracts.Verdict verdict = verdict(results);
        var view = new Rca1Contracts.NormalizedP1ComparisonView(
                referenceExact, candidateExact, referenceDerived, candidateDerived, referenceWindows, candidateWindows);
        return new Rca1Contracts.P1ComparisonResult(fixture, view, verdict, results,
                Rca1Contracts.mismatches(fixture, results));
    }

    private static Rca1Contracts.DimensionResult checkpoint(Rca1Contracts.CheckpointMetadata expected,
            Rca1Contracts.CheckpointMetadata actual, Instant referenceTime) {
        String e = expected.ref()+"|"+expected.sequence()+"|"+expected.recordedAt();
        String a = actual.ref()+"|"+actual.sequence()+"|"+actual.recordedAt();
        if (!expected.ref().equals(actual.ref()) || expected.sequence()!=actual.sequence()) {
            return result(Rca1Contracts.Dimension.SOURCE_CHECKPOINT_PARITY,
                    Rca1Contracts.Classification.SOURCE_CHECKPOINT_MISMATCH,e,a,"Checkpoint reference or sequence differs.",true);
        }
        long age = Duration.between(actual.recordedAt(), referenceTime).getSeconds();
        if (age < 0 || age >= actual.maxStalenessSeconds()) {
            return result(Rca1Contracts.Dimension.SOURCE_CHECKPOINT_PARITY,
                    Rca1Contracts.Classification.SOURCE_STALE,e,a,"Candidate checkpoint is stale or inverted.",true);
        }
        return result(Rca1Contracts.Dimension.SOURCE_CHECKPOINT_PARITY,
                Rca1Contracts.Classification.MATCH_EXACT,e,a,"Checkpoint matches and is fresh.",false);
    }

    private static Rca1Contracts.DimensionResult lineage(Rca1Contracts.LineageMetadata expected,
            Rca1Contracts.LineageMetadata actual) {
        if (!expected.fingerprint().equals(actual.fingerprint())) {
            return result(Rca1Contracts.Dimension.SOURCE_LINEAGE_PARITY,
                    Rca1Contracts.Classification.LINEAGE_MISMATCH,expected.fingerprint(),actual.fingerprint(),
                    "Lineage fingerprint differs.",true);
        }
        return result(Rca1Contracts.Dimension.SOURCE_LINEAGE_PARITY,Rca1Contracts.Classification.MATCH_EXACT,
                expected.fingerprint(),actual.fingerprint(),"Lineage matches.",false);
    }

    private static Rca1Contracts.DimensionResult gap(Rca1Contracts.Dimension dimension, boolean available, String detail) {
        if (available) return result(dimension,Rca1Contracts.Classification.MATCH_EXACT,"available","available",detail,false);
        return result(dimension,Rca1Contracts.Classification.EXPECTED_SEMANTIC_GAP,"authoritative-only","candidate-missing",detail,false);
    }

    private static Rca1Contracts.DimensionResult equal(Rca1Contracts.Dimension dimension,
            Rca1Contracts.NormalizedValue expected, Rca1Contracts.NormalizedValue actual,
            Rca1Contracts.Classification match, String detail) {
        if (expected.equals(actual)) return new Rca1Contracts.DimensionResult(dimension,match,expected,actual,detail,false);
        return new Rca1Contracts.DimensionResult(dimension,Rca1Contracts.Classification.RECONCILIATION_INCONCLUSIVE,
                expected,actual,detail,true);
    }

    private static Rca1Contracts.DimensionResult result(Rca1Contracts.Dimension dimension,
            Rca1Contracts.Classification classification, String expected, String actual, String detail, boolean blocking) {
        return new Rca1Contracts.DimensionResult(dimension,classification,
                Rca1Normalizer.scalar(expected),Rca1Normalizer.scalar(actual),detail,blocking);
    }

    private static Rca1Contracts.Verdict verdict(List<Rca1Contracts.DimensionResult> results) {
        if (results.stream().anyMatch(r -> r.dimension()==Rca1Contracts.Dimension.IDENTITY_BLOCKED && r.blocking()))
            return Rca1Contracts.Verdict.BLOCKED_BY_IDENTITY;
        if (results.stream().anyMatch(Rca1Contracts.DimensionResult::blocking))
            return Rca1Contracts.Verdict.BLOCKED_BY_UNEXPECTED_MISMATCH;
        return Rca1Contracts.Verdict.RECONCILED_WITH_EXPECTED_GAPS;
    }
}
