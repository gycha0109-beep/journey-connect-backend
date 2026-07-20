package com.jc.intelligence.readiness.search.v1;

import com.jc.intelligence.contract.v1.version.ContractId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record SearchActivationPrerequisiteMatrixV1(
        ContractId contractVersion,
        List<SearchActivationPrerequisiteV1> prerequisites) {
    private static final Set<String> PROPOSAL_REQUIRED = Set.of(
            "backend_explore_inventory",
            "controlled_hook_proposal",
            "disabled_mode_equivalence",
            "production_hook_not_inserted",
            "unified_regression_task");
    public SearchActivationPrerequisiteMatrixV1 {
        if (!SearchReadinessContractIds.PREREQUISITE_MATRIX.equals(contractVersion)) {
            throw new IllegalArgumentException("unexpected contractVersion");
        }
        if (prerequisites == null || prerequisites.isEmpty()) throw new IllegalArgumentException("prerequisites are required");
        ArrayList<SearchActivationPrerequisiteV1> copy = new ArrayList<>(prerequisites);
        if (copy.stream().anyMatch(java.util.Objects::isNull)) throw new IllegalArgumentException("prerequisites contain null");
        copy.sort(Comparator.comparing(SearchActivationPrerequisiteV1::prerequisiteId));
        Set<String> ids = new HashSet<>();
        for (SearchActivationPrerequisiteV1 prerequisite : copy) {
            if (!ids.add(prerequisite.prerequisiteId())) throw new IllegalArgumentException("duplicate prerequisiteId");
        }
        prerequisites = List.copyOf(copy);
    }
    public boolean controlledHookProposalReady() {
        return PROPOSAL_REQUIRED.stream().allMatch(required -> prerequisites.stream().anyMatch(item ->
                item.prerequisiteId().equals(required) && item.status() == SearchPrerequisiteStatus.RESOLVED));
    }
    public long activationBlockerCount() {
        return prerequisites.stream().filter(SearchActivationPrerequisiteV1::blocksActivation).count();
    }
    public long cutoverBlockerCount() {
        return prerequisites.stream().filter(SearchActivationPrerequisiteV1::blocksCutover).count();
    }
    public SearchShadowReadinessDecision proposalDecision() {
        if (!controlledHookProposalReady()) return SearchShadowReadinessDecision.NOT_READY;
        boolean architectureBlocker = prerequisites.stream().anyMatch(item ->
                item.prerequisiteId().equals("architecture_change_required")
                        && item.status() != SearchPrerequisiteStatus.NOT_REQUIRED_FOR_SHADOW
                        && item.status() != SearchPrerequisiteStatus.RESOLVED);
        if (architectureBlocker) return SearchShadowReadinessDecision.HOLD_FOR_ARCHITECTURE_CHANGE;
        return SearchShadowReadinessDecision.READY_FOR_CONTROLLED_HOOK_PROPOSAL;
    }
    public SearchShadowReadinessDecision activationDecision() {
        if (proposalDecision() != SearchShadowReadinessDecision.READY_FOR_CONTROLLED_HOOK_PROPOSAL) {
            return proposalDecision();
        }
        return activationBlockerCount() == 0
                ? SearchShadowReadinessDecision.READY_FOR_CONTROLLED_HOOK_PROPOSAL
                : SearchShadowReadinessDecision.HOLD_FOR_OWNER_DECISIONS;
    }
}
