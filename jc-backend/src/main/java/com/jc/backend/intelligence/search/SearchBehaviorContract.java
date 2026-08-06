package com.jc.backend.intelligence.search;

import java.time.Duration;

public final class SearchBehaviorContract {

    public static final String SCHEMA_VERSION = "search-behavior-event-v1";
    public static final Duration MAX_FUTURE_SKEW = Duration.ofMinutes(5);
    public static final Duration MAX_EVENT_AGE = Duration.ofDays(30);

    private SearchBehaviorContract() {}
}
