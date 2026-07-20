package com.jc.intelligence.wiring.search.v1;

import java.time.Duration;

public interface SearchShadowExecutor {
    boolean available();
    int queueCapacity();
    int maxConcurrency();
    <T> SearchShadowExecutorResultV1<T> submit(SearchShadowTask<T> task, Duration timeout);
}
