package com.jc.backend.intelligence.compat.recommendation;

import com.jc.backend.recommendation.persistence.RecommendationStorageTypes.SnapshotKind;
import com.jc.intelligence.contract.v1.authority.ExposureSourceId;
import com.jc.intelligence.contract.v1.snapshot.SnapshotRole;
import com.jc.intelligence.contract.v1.version.ContractId;
import com.jc.intelligence.contract.v1.version.IntelligenceContractIds;

public final class RecommendationSnapshotRoleAdapterV1 {
    public SnapshotRoleMappingV1 adapt(SnapshotKind sourceKind) {
        java.util.Objects.requireNonNull(sourceKind, "sourceKind");
        return switch (sourceKind) {
            case RANKING_INPUT_V1 -> new SnapshotRoleMappingV1(
                    sourceKind.value(),
                    SnapshotRole.INPUT,
                    IntelligenceContractIds.INTELLIGENCE_INPUT_SNAPSHOT,
                    false,
                    null);
            case DIVERSITY_METADATA_V1, EXPLORATION_METADATA_V1 -> new SnapshotRoleMappingV1(
                    sourceKind.value(),
                    SnapshotRole.CANDIDATE,
                    IntelligenceContractIds.INTELLIGENCE_CANDIDATE_SNAPSHOT,
                    true,
                    null);
            case RANKING_RESULT_V1 -> new SnapshotRoleMappingV1(
                    sourceKind.value(),
                    SnapshotRole.OUTPUT,
                    IntelligenceContractIds.INTELLIGENCE_OUTPUT_SNAPSHOT,
                    false,
                    null);
            case EXPOSURE_EVENT_V1 -> new SnapshotRoleMappingV1(
                    sourceKind.value(),
                    SnapshotRole.EXPOSURE_EVIDENCE,
                    null,
                    false,
                    ExposureSourceId.RECOMMENDATION_GENERAL_EXPOSURE_V1);
        };
    }

    public record SnapshotRoleMappingV1(
            String existingSnapshotKind,
            SnapshotRole commonRole,
            ContractId commonContractId,
            boolean dependencyOnly,
            ExposureSourceId exposureSourceId) {
        public SnapshotRoleMappingV1 {
            java.util.Objects.requireNonNull(existingSnapshotKind, "existingSnapshotKind");
            java.util.Objects.requireNonNull(commonRole, "commonRole");
            if (commonRole != SnapshotRole.EXPOSURE_EVIDENCE) {
                java.util.Objects.requireNonNull(commonContractId, "commonContractId");
            } else if (commonContractId != null) {
                throw new IllegalArgumentException(
                        "No SC-1 common snapshot contract is reserved for exposure evidence");
            }
        }
    }
}
