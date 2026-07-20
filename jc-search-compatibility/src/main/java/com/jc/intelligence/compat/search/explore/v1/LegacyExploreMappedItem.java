package com.jc.intelligence.compat.search.explore.v1;

import com.jc.intelligence.contract.v1.identity.EntityRef;
import com.jc.intelligence.contract.v1.search.SearchEligibilityState;
import com.jc.intelligence.contract.v1.search.SearchEntityType;
import com.jc.intelligence.contract.v1.search.SearchVisibilityState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public record LegacyExploreMappedItem(
        EntityRef entityRef, SearchEntityType entityType, String sourceId, int sourcePosition, Integer finalPosition,
        Double retrievalScore, Instant retrievedAt, String sourceSnapshotRef,
        SearchEligibilityState eligibilityState, SearchVisibilityState visibilityState,
        LegacyExploreItemView legacyItem, LegacyExploreCompatibilityExplanation explanation,
        List<LegacyExploreWarningCode> warningCodes) {
    public LegacyExploreMappedItem {
        java.util.Objects.requireNonNull(entityRef, "entityRef");
        java.util.Objects.requireNonNull(entityType, "entityType");
        java.util.Objects.requireNonNull(sourceId, "sourceId");
        java.util.Objects.requireNonNull(legacyItem, "legacyItem");
        java.util.Objects.requireNonNull(explanation, "explanation");
        java.util.Objects.requireNonNull(eligibilityState, "eligibilityState");
        java.util.Objects.requireNonNull(visibilityState, "visibilityState");
        warningCodes = List.copyOf(new ArrayList<>(java.util.Objects.requireNonNull(warningCodes, "warningCodes")));
        if (!entityType.wireValue().equals(entityRef.entityType()) || !sourceId.equals(entityRef.sourceId())) {
            throw new IllegalArgumentException("mapped entity type/source must match entityRef");
        }
        if (sourcePosition < 1) throw new IllegalArgumentException("sourcePosition must be 1-based");
        if (finalPosition != null || retrievalScore != null || retrievedAt != null || sourceSnapshotRef != null) {
            throw new IllegalArgumentException("legacy mapping must not invent final rank, score, retrieval time, or snapshot");
        }
        if (eligibilityState != SearchEligibilityState.UNKNOWN || visibilityState != SearchVisibilityState.UNKNOWN) {
            throw new IllegalArgumentException("legacy response payload does not materialize eligibility/visibility evidence");
        }
    }
}
