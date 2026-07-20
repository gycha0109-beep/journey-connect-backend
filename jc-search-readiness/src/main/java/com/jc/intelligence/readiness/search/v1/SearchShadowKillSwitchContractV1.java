package com.jc.intelligence.readiness.search.v1;

import com.jc.intelligence.contract.v1.version.ContractId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record SearchShadowKillSwitchContractV1(
        ContractId contractVersion,
        List<SearchKillSwitchStepV1> steps) {
    public SearchShadowKillSwitchContractV1 {
        if (!SearchReadinessContractIds.KILL_SWITCH.equals(contractVersion)) throw new IllegalArgumentException("unexpected contractVersion");
        if (steps == null || steps.size() != 5) throw new IllegalArgumentException("five kill-switch steps are required");
        ArrayList<SearchKillSwitchStepV1> copy = new ArrayList<>(steps);
        if (copy.stream().anyMatch(java.util.Objects::isNull)) throw new IllegalArgumentException("steps contain null");
        copy.sort(Comparator.comparingInt(SearchKillSwitchStepV1::priority));
        SearchKillSwitchKey[] expected = SearchKillSwitchKey.values();
        for (int index = 0; index < copy.size(); index++) {
            if (copy.get(index).priority() != index + 1 || copy.get(index).key() != expected[index]) {
                throw new IllegalArgumentException("kill-switch priority is not canonical");
            }
        }
        steps = List.copyOf(copy);
    }
    public boolean failsClosedForShadow() { return true; }
    public boolean failsOpenForLegacyResponse() { return true; }
}
