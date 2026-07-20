package com.jc.intelligence.compat.search.explore.v1;

import java.util.ArrayList;
import java.util.List;

public record LegacyExploreCompatibilityResult(
        LegacyExploreCompatibilityStatus status, LegacyExploreMappedRequest request,
        List<LegacyExploreMappedItem> items, LegacyExplorePageMetadata pageMetadata,
        LegacyExploreCompatibilityEvidence evidence, LegacyExploreMappingFailure failure,
        boolean searchCursorAvailable, boolean searchRunAuthority, boolean searchExposureAuthority) {
    public LegacyExploreCompatibilityResult {
        java.util.Objects.requireNonNull(status, "status");
        items = items == null ? List.of() : List.copyOf(new ArrayList<>(items));
        if (items.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("items must not contain null");
        }
        if (searchCursorAvailable || searchRunAuthority || searchExposureAuthority) {
            throw new IllegalArgumentException("legacy compatibility result cannot expose cursor/run/exposure authority");
        }
        if (status == LegacyExploreCompatibilityStatus.SUCCESS
                && (failure != null || request == null || pageMetadata == null || evidence == null)) {
            throw new IllegalArgumentException("success result requires request/page/evidence and no failure");
        }
        if (status != LegacyExploreCompatibilityStatus.SUCCESS && failure == null) {
            throw new IllegalArgumentException("non-success result requires failure");
        }
    }
}
