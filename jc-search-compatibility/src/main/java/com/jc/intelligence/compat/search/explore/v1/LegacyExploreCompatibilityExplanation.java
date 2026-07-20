package com.jc.intelligence.compat.search.explore.v1;

import java.util.ArrayList;
import java.util.List;

public record LegacyExploreCompatibilityExplanation(
        List<LegacyExploreExplanationCode> factCodes, boolean searchRankingAuthority, boolean matchFieldAuthority) {
    public LegacyExploreCompatibilityExplanation {
        factCodes = factCodes == null ? List.of() : List.copyOf(new ArrayList<>(factCodes));
        if (factCodes.stream().anyMatch(java.util.Objects::isNull)
                || new java.util.LinkedHashSet<>(factCodes).size() != factCodes.size()) {
            throw new IllegalArgumentException("compatibility explanation facts must be non-null and unique");
        }
        if (searchRankingAuthority || matchFieldAuthority) {
            throw new IllegalArgumentException("legacy explanation cannot activate Search ranking or match-field authority");
        }
    }
}
