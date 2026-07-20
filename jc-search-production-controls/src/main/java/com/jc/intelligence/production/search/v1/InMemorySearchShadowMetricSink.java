package com.jc.intelligence.production.search.v1;

import java.time.Duration;import java.util.LinkedHashMap;import java.util.Map;

public final class InMemorySearchShadowMetricSink implements SearchShadowMetricSink {
 private final Map<SearchShadowMetricName,Long> counts=new LinkedHashMap<>();
 public synchronized void increment(SearchShadowMetricName n,Map<String,String> t){validate(t);counts.merge(n,1L,Long::sum);} public synchronized void recordDuration(SearchShadowMetricName n,Duration d,Map<String,String> t){if(d==null||d.isNegative())throw new IllegalArgumentException("duration invalid");validate(t);counts.merge(n,1L,Long::sum);} public synchronized void recordGauge(SearchShadowMetricName n,long v,Map<String,String> t){if(v<0)throw new IllegalArgumentException("gauge invalid");validate(t);counts.put(n,v);} public synchronized long value(SearchShadowMetricName n){return counts.getOrDefault(n,0L);} private static void validate(Map<String,String> tags){if(tags==null||tags.size()>4)throw new IllegalArgumentException("bounded tags required");for(var e:tags.entrySet()){if(!e.getKey().matches("[a-z][a-z0-9_]{0,31}")||!e.getValue().matches("[a-z0-9_]{1,32}"))throw new IllegalArgumentException("tag invalid");if(e.getKey().matches(".*(query|user|session|jwt|post|document|correlation).*"))throw new IllegalArgumentException("high-cardinality tag prohibited");}}
}
