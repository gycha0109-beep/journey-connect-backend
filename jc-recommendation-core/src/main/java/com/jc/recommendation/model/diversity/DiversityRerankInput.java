package com.jc.recommendation.model.diversity;

import com.jc.recommendation.model.ranking.RankedCandidate;
import com.jc.recommendation.policy.DiversityPolicy;
import java.util.List;
import java.util.Objects;

public record DiversityRerankInput(
        String rankingSnapshotId, String metadataSnapshotId, String rankingPolicyVersion,
        String scorePolicyVersion, List<RankedCandidate> baseRankedCandidates,
        List<DiversityCandidateMetadata> candidateMetadata, DiversityPolicy policy
) {
    public DiversityRerankInput {
        Objects.requireNonNull(rankingSnapshotId, "rankingSnapshotId");
        Objects.requireNonNull(metadataSnapshotId, "metadataSnapshotId");
        Objects.requireNonNull(rankingPolicyVersion, "rankingPolicyVersion");
        Objects.requireNonNull(scorePolicyVersion, "scorePolicyVersion");
        baseRankedCandidates = List.copyOf(baseRankedCandidates);
        candidateMetadata = List.copyOf(candidateMetadata);
    }
}
