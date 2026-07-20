package com.jc.intelligence.production.search.v1;
import java.time.Duration;import java.util.Map;
public final class NoOpSearchShadowMetricSink implements SearchShadowMetricSink {
 public void increment(SearchShadowMetricName n,Map<String,String> t){} public void recordDuration(SearchShadowMetricName n,Duration d,Map<String,String> t){} public void recordGauge(SearchShadowMetricName n,long v,Map<String,String> t){}
}
