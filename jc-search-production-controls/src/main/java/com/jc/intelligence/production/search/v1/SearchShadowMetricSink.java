package com.jc.intelligence.production.search.v1;

import java.time.Duration;
import java.util.Map;

public interface SearchShadowMetricSink {
    void increment(SearchShadowMetricName name,Map<String,String> boundedTags);
    void recordDuration(SearchShadowMetricName name,Duration duration,Map<String,String> boundedTags);
    void recordGauge(SearchShadowMetricName name,long value,Map<String,String> boundedTags);
}
