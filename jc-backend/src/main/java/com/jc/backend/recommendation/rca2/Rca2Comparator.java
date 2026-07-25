package com.jc.backend.recommendation.rca2;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class Rca2Comparator {
    private static final Set<String> P2_EVENTS = Set.of("click", "like", "save", "share");

    public Rca2RuntimeContracts.ComparisonResult compare(
            Rca2RuntimeContracts.PrimarySnapshot primary,
            Rca2RuntimeContracts.CandidateResult candidate) {
        List<String> inventory = new ArrayList<>();
        if (primary.lane() != candidate.lane()) {
            return result(primary, candidate, Rca2RuntimeContracts.ComparisonClass.AUTHORITY_MISMATCH, true, inventory);
        }
        var pc = primary.checkpoint();
        var cc = candidate.checkpoint();
        long lag = Math.max(0L, Duration.between(cc.capturedAtUtc(), pc.capturedAtUtc()).toMillis());
        if (!pc.opaqueRef().equals(cc.opaqueRef()) || cc.monotonicSequence() < pc.monotonicSequence()
                || !pc.sourceVersion().equals(cc.sourceVersion()) || !pc.schemaVersion().equals(cc.schemaVersion())
                || cc.capturedAtUtc().isAfter(pc.capturedAtUtc())) {
            inventory.add("INCOMPATIBLE_CHECKPOINT");
            return new Rca2RuntimeContracts.ComparisonResult(primary.lane(),
                    Rca2RuntimeContracts.ComparisonClass.CHECKPOINT_MISMATCH, true, lag, inventory);
        }
        if (!primary.lineage().fingerprint().equals(candidate.lineage().fingerprint())) {
            inventory.add("LINEAGE_FINGERPRINT_MISMATCH");
            return new Rca2RuntimeContracts.ComparisonResult(primary.lane(),
                    Rca2RuntimeContracts.ComparisonClass.LINEAGE_MISMATCH, true, lag, inventory);
        }
        if (candidate.stale()) {
            inventory.add("RUNTIME_FRESHNESS_POLICY_BLOCKED_PENDING_MEASUREMENT");
            return new Rca2RuntimeContracts.ComparisonResult(primary.lane(),
                    Rca2RuntimeContracts.ComparisonClass.STALE_CANDIDATE, true, lag, inventory);
        }
        if (primary.lane() == Rca2RuntimeContracts.Lane.P1) {
            for (String gap : candidate.declaredGaps()) {
                if (Rca2RuntimeContracts.P1_EXPECTED_GAPS.contains(gap)) inventory.add(gap);
                else return new Rca2RuntimeContracts.ComparisonResult(primary.lane(),
                        Rca2RuntimeContracts.ComparisonClass.RESULT_MISMATCH, true, lag, List.of(gap));
            }
            if (!primary.digest().equals(candidate.digest()) || primary.itemCount() != candidate.resultSize()) {
                inventory.add("P1_RESULT_DIGEST_OR_SIZE_MISMATCH");
                return new Rca2RuntimeContracts.ComparisonResult(primary.lane(),
                        Rca2RuntimeContracts.ComparisonClass.RESULT_MISMATCH, false, lag, inventory);
            }
            return new Rca2RuntimeContracts.ComparisonResult(primary.lane(),
                    inventory.isEmpty() ? Rca2RuntimeContracts.ComparisonClass.MATCH
                            : Rca2RuntimeContracts.ComparisonClass.EXPECTED_GAP,
                    false, lag, inventory);
        }
        if (!"recommendation_p2_experiment_exposure".equals(candidate.exposureAuthority())
                || candidate.outcomeWindowSeconds() != 604_800L
                || !P2_EVENTS.containsAll(candidate.engagementEvents())
                || !"BOUND_RECOMMENDATION_RUN_ONLY".equals(candidate.fallbackSource())
                || !candidate.oneObservationKeyValid()) {
            inventory.add("P2_AUTHORITY_OR_SEMANTIC_MISMATCH");
            return new Rca2RuntimeContracts.ComparisonResult(primary.lane(),
                    Rca2RuntimeContracts.ComparisonClass.AUTHORITY_MISMATCH, true, lag, inventory);
        }
        for (String gap : candidate.declaredGaps()) {
            if (Rca2RuntimeContracts.P2_MIGRATION_GAPS.contains(gap)) inventory.add(gap);
            else return new Rca2RuntimeContracts.ComparisonResult(primary.lane(),
                    Rca2RuntimeContracts.ComparisonClass.RESULT_MISMATCH, true, lag, List.of(gap));
        }
        if (!primary.digest().equals(candidate.digest()) || primary.itemCount() != candidate.resultSize()) {
            inventory.add("P2_RESULT_DIGEST_OR_SIZE_MISMATCH");
            return new Rca2RuntimeContracts.ComparisonResult(primary.lane(),
                    Rca2RuntimeContracts.ComparisonClass.RESULT_MISMATCH, false, lag, inventory);
        }
        return new Rca2RuntimeContracts.ComparisonResult(primary.lane(),
                inventory.isEmpty() ? Rca2RuntimeContracts.ComparisonClass.MATCH
                        : Rca2RuntimeContracts.ComparisonClass.MIGRATION_GAP,
                false, lag, inventory);
    }

    private static Rca2RuntimeContracts.ComparisonResult result(
            Rca2RuntimeContracts.PrimarySnapshot primary,
            Rca2RuntimeContracts.CandidateResult candidate,
            Rca2RuntimeContracts.ComparisonClass classification,
            boolean discard,
            List<String> inventory) {
        long lag = Math.max(0L, Duration.between(candidate.checkpoint().capturedAtUtc(),
                primary.checkpoint().capturedAtUtc()).toMillis());
        return new Rca2RuntimeContracts.ComparisonResult(primary.lane(), classification, discard, lag, inventory);
    }
}
