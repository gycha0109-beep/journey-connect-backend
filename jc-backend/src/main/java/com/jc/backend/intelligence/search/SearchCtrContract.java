package com.jc.backend.intelligence.search;

import java.time.Duration;

public final class SearchCtrContract {

    public static final String METRIC_ID = "search-click-through-rate-v1";
    public static final String METRIC_VERSION = "search-ctr-projection-v1";
    public static final String ATTRIBUTION_VERSION = "search-click-attribution-v1";
    public static final String PROVISIONAL_STATUS = "PROVISIONAL";
    public static final Duration ATTRIBUTION_WINDOW = Duration.ofMinutes(30);
    public static final int BASIS_POINT_SCALE = 10_000;

    private SearchCtrContract() {}
}
