package com.jc.recommendation.policy;

import com.jc.recommendation.model.diversity.DiversityDimension;
import com.jc.recommendation.model.diversity.DiversityExposureCaps;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record DiversityPolicy(
        String policyVersion, Instant effectiveFrom, String expectedRankingPolicyVersion,
        String expectedScorePolicyVersion, int maximumCandidateCount, int exposureWindowSize,
        int maxPromotionDistance, int maxDemotionDistance, DiversityExposureCaps exposureCaps,
        List<DiversityDimension> relaxationOrder, String missingMetadataBehavior, String scoreMutation,
        String candidateRemoval, String candidateInsertion, String paginationStage, String explorationStage
) implements VersionedPolicy {
    public DiversityPolicy {
        Objects.requireNonNull(policyVersion, "policyVersion");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        Objects.requireNonNull(expectedRankingPolicyVersion, "expectedRankingPolicyVersion");
        Objects.requireNonNull(expectedScorePolicyVersion, "expectedScorePolicyVersion");
        Objects.requireNonNull(exposureCaps, "exposureCaps");
        relaxationOrder = List.copyOf(relaxationOrder);
        Objects.requireNonNull(missingMetadataBehavior, "missingMetadataBehavior");
        Objects.requireNonNull(scoreMutation, "scoreMutation");
        Objects.requireNonNull(candidateRemoval, "candidateRemoval");
        Objects.requireNonNull(candidateInsertion, "candidateInsertion");
        Objects.requireNonNull(paginationStage, "paginationStage");
        Objects.requireNonNull(explorationStage, "explorationStage");
    }
}
