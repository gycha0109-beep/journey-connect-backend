package com.jc.backend.recommendation.rca2;

import java.util.Set;

@FunctionalInterface
public interface Rca2CandidateAdapter {
    Rca2RuntimeContracts.CandidateResult compute(
            Rca2RuntimeContracts.ShadowRequest request,
            Rca2RuntimeContracts.Deadline deadline) throws Exception;

    default Rca2CandidateSourceDecision sourceDecision() {
        return Rca2CandidateSourceDecision.unresolved();
    }

    default boolean readOnly() { return true; }
    default boolean servingAllowed() { return false; }
    default String fallbackPolicy() { return Rca2RuntimeContracts.SHADOW_FAILURE_FALLBACK; }

    static Rca2CandidateAdapter isolatedContractOnly() {
        return (request, deadline) -> {
            var primary = request.primary();
            Set<String> gaps = primary.lane() == Rca2RuntimeContracts.Lane.P1
                    ? Rca2RuntimeContracts.P1_EXPECTED_GAPS
                    : Rca2RuntimeContracts.P2_MIGRATION_GAPS;
            return new Rca2RuntimeContracts.CandidateResult(
                    primary.lane(), primary.digest(), primary.itemCount(), primary.checkpoint(), primary.lineage(),
                    false, false, primary.itemCount() == 0, gaps,
                    "recommendation_p2_experiment_exposure", 604_800L,
                    Set.of("click", "like", "save", "share"), "BOUND_RECOMMENDATION_RUN_ONLY", true);
        };
    }
}
