package com.jc.backend.intelligence.search;

import java.time.Duration;

public final class SearchExposureContract {

    public static final String SCHEMA_VERSION = "search-exposure-v1";
    public static final String IDENTITY_SCHEME = "platform_subject_v1";
    public static final String SURFACE = "search";
    public static final String RESULT_ENTITY_TYPE = "post";
    public static final String CANDIDATE_VISIBILITY_RULE_VERSION = "search-item-visible-v1";
    public static final int MAX_BATCH_SIZE = 100;
    public static final int MAX_PAGE_SIZE = 100;
    public static final int MIN_VISIBLE_RATIO_BASIS_POINTS = 5_000;
    public static final int MAX_VISIBLE_RATIO_BASIS_POINTS = 10_000;
    public static final long MIN_DWELL_MILLISECONDS = 1_000L;
    public static final long MAX_DWELL_MILLISECONDS = 86_400_000L;
    public static final Duration MAX_FUTURE_SKEW = Duration.ofMinutes(5);
    public static final Duration MAX_CONTEXT_CLOCK_SKEW = Duration.ofSeconds(5);

    private SearchExposureContract() {}
}
