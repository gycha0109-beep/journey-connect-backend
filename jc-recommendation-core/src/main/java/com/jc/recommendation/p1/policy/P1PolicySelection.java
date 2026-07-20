package com.jc.recommendation.p1.policy;

import java.util.List;
import java.util.Objects;

public record P1PolicySelection(
        P1ExperimentAssignment assignment,
        P1PolicyBundle policyBundle,
        List<String> reasons) {

    public P1PolicySelection {
        Objects.requireNonNull(assignment, "assignment");
        Objects.requireNonNull(policyBundle, "policyBundle");
        reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons"));
        if (reasons.isEmpty()) {
            throw new IllegalArgumentException("policy selection reasons must not be empty");
        }
    }
}
