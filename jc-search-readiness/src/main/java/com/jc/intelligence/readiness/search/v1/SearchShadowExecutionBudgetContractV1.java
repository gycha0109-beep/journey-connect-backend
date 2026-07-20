package com.jc.intelligence.readiness.search.v1;

import com.jc.intelligence.contract.v1.version.ContractId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record SearchShadowExecutionBudgetContractV1(
        ContractId contractVersion,
        List<SearchShadowBudgetEntryV1> entries) {
    public SearchShadowExecutionBudgetContractV1 {
        if (!SearchReadinessContractIds.BUDGET.equals(contractVersion)) throw new IllegalArgumentException("unexpected contractVersion");
        if (entries == null || entries.isEmpty()) throw new IllegalArgumentException("budget entries are required");
        ArrayList<SearchShadowBudgetEntryV1> copy = new ArrayList<>(entries);
        if (copy.stream().anyMatch(java.util.Objects::isNull)) throw new IllegalArgumentException("budget entries contain null");
        copy.sort(Comparator.comparing(entry -> entry.key().wireValue()));
        Set<SearchBudgetKey> keys = new HashSet<>();
        for (SearchShadowBudgetEntryV1 entry : copy) if (!keys.add(entry.key())) throw new IllegalArgumentException("duplicate budget key");
        entries = List.copyOf(copy);
    }
    public boolean productionApproved() {
        return entries.size() == SearchBudgetKey.values().length
                && entries.stream().allMatch(entry -> entry.status() == SearchPrerequisiteStatus.RESOLVED);
    }
}
