package com.jc.intelligence.compat.search.explore.v1;

import java.util.List;

public final class LegacyExploreCompatibilityPolicy {
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 2000;
    public static final List<LegacyExploreWarningCode> BASE_WARNINGS = List.of(
            LegacyExploreWarningCode.LEGACY_OFFSET_PAGINATION,
            LegacyExploreWarningCode.SEARCH_CURSOR_UNAVAILABLE,
            LegacyExploreWarningCode.SEARCH_RUN_AUTHORITY_UNAVAILABLE,
            LegacyExploreWarningCode.SEARCH_EXPOSURE_AUTHORITY_UNAVAILABLE,
            LegacyExploreWarningCode.RETRIEVAL_SCORE_UNAVAILABLE,
            LegacyExploreWarningCode.SOURCE_SNAPSHOT_UNAVAILABLE,
            LegacyExploreWarningCode.RANKING_POLICY_AUTHORITY_UNAVAILABLE,
            LegacyExploreWarningCode.MATCH_FIELD_UNAVAILABLE,
            LegacyExploreWarningCode.VISIBILITY_EVIDENCE_NOT_MATERIALIZED);

    private LegacyExploreCompatibilityPolicy() { }
}
