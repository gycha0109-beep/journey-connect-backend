package com.jc.intelligence.readiness.search.v1;

import com.jc.intelligence.contract.v1.version.ContractId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record SearchShadowRollbackContractV1(
        ContractId contractVersion,
        List<SearchRollbackStepV1> steps) {
    public SearchShadowRollbackContractV1 {
        if (!SearchReadinessContractIds.ROLLBACK.equals(contractVersion)) throw new IllegalArgumentException("unexpected contractVersion");
        if (steps == null || steps.size() != SearchRollbackLevel.values().length) throw new IllegalArgumentException("all rollback levels are required");
        ArrayList<SearchRollbackStepV1> copy = new ArrayList<>(steps);
        if (copy.stream().anyMatch(java.util.Objects::isNull)) throw new IllegalArgumentException("steps contain null");
        copy.sort(Comparator.comparingInt(step -> step.level().ordinal()));
        for (int index = 0; index < copy.size(); index++) {
            if (copy.get(index).level().ordinal() != index) throw new IllegalArgumentException("rollback levels are not canonical");
        }
        steps = List.copyOf(copy);
    }
}
