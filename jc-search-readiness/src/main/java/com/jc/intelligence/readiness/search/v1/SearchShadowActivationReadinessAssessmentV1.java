package com.jc.intelligence.readiness.search.v1;

import com.jc.intelligence.contract.v1.version.ContractId;
import com.jc.intelligence.contract.v1.version.ProducerBuildId;
import java.time.Instant;
import java.util.Objects;

public record SearchShadowActivationReadinessAssessmentV1(
        ContractId contractVersion,
        SearchShadowReadinessDecision proposalDecision,
        SearchShadowReadinessDecision productionActivationDecision,
        SearchActivationPrerequisiteMatrixV1 prerequisiteMatrix,
        boolean disabledModeEquivalent,
        boolean productionHookInserted,
        boolean productionActivationEnabled,
        Instant assessedAt,
        ProducerBuildId producerBuildId,
        SearchShadowReadinessAuthorityV1 authority) {
    public SearchShadowActivationReadinessAssessmentV1 {
        if (!SearchReadinessContractIds.READINESS.equals(contractVersion)) throw new IllegalArgumentException("unexpected contractVersion");
        Objects.requireNonNull(proposalDecision, "proposalDecision");
        Objects.requireNonNull(productionActivationDecision, "productionActivationDecision");
        Objects.requireNonNull(prerequisiteMatrix, "prerequisiteMatrix");
        Objects.requireNonNull(assessedAt, "assessedAt");
        Objects.requireNonNull(producerBuildId, "producerBuildId");
        if (!SearchShadowReadinessAuthorityV1.legacyOnly().equals(authority)) throw new IllegalArgumentException("authority must remain legacy-only");
        if (productionHookInserted || productionActivationEnabled) throw new IllegalArgumentException("IP-8 cannot insert or activate production hook");
        if (proposalDecision != prerequisiteMatrix.proposalDecision()) throw new IllegalArgumentException("proposalDecision mismatch");
        if (productionActivationDecision != prerequisiteMatrix.activationDecision()) throw new IllegalArgumentException("activationDecision mismatch");
        if (proposalDecision == SearchShadowReadinessDecision.READY_FOR_CONTROLLED_HOOK_PROPOSAL && !disabledModeEquivalent) {
            throw new IllegalArgumentException("proposal readiness requires disabled-mode equivalence");
        }
    }
}
