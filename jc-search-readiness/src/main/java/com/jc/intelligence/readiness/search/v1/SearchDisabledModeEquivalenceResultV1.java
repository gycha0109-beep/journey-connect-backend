package com.jc.intelligence.readiness.search.v1;

import java.util.ArrayList;
import java.util.List;

public record SearchDisabledModeEquivalenceResultV1(boolean equivalent, List<String> violationCodes) {
    public SearchDisabledModeEquivalenceResultV1 {
        violationCodes = List.copyOf(new ArrayList<>(violationCodes == null ? List.of() : violationCodes));
        if (violationCodes.stream().anyMatch(code -> code == null || !code.matches("[a-z][a-z0-9_]{0,63}"))) {
            throw new IllegalArgumentException("violationCodes must be lowercase_snake_case");
        }
        if (equivalent != violationCodes.isEmpty()) throw new IllegalArgumentException("equivalent flag is inconsistent");
    }
}
