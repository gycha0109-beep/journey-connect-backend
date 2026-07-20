package com.jc.backend.search.shadow.production;

import com.jc.intelligence.production.search.v1.SearchShadowMetricName;
import com.jc.intelligence.production.search.v1.SearchShadowMetricSink;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class MicrometerSearchShadowMetricSink implements SearchShadowMetricSink {
    private static final String PREFIX = "journey.search.";
    private final MeterRegistry registry;
    private final Map<String, AtomicLong> gauges = new ConcurrentHashMap<>();

    public MicrometerSearchShadowMetricSink(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public void increment(SearchShadowMetricName name, Map<String, String> boundedTags) {
        try {
            registry.counter(metricName(name), tags(boundedTags)).increment();
        } catch (RuntimeException ignored) {
            // Metric failures never reach the legacy endpoint.
        }
    }

    @Override
    public void recordDuration(
            SearchShadowMetricName name,
            Duration duration,
            Map<String, String> boundedTags) {
        if (duration == null || duration.isNegative()) {
            return;
        }
        try {
            registry.timer(metricName(name), tags(boundedTags)).record(duration);
        } catch (RuntimeException ignored) {
            // Metric failures never reach the legacy endpoint.
        }
    }

    @Override
    public void recordGauge(
            SearchShadowMetricName name,
            long value,
            Map<String, String> boundedTags) {
        if (value < 0) {
            return;
        }
        try {
            String key = metricName(name) + '|' + canonicalTags(boundedTags);
            AtomicLong holder = gauges.computeIfAbsent(key, ignored -> {
                AtomicLong created = new AtomicLong();
                registry.gauge(metricName(name), tags(boundedTags), created);
                return created;
            });
            holder.set(value);
        } catch (RuntimeException ignored) {
            // Metric failures never reach the legacy endpoint.
        }
    }

    private static String metricName(SearchShadowMetricName name) {
        return PREFIX + Objects.requireNonNull(name, "name").wireValue();
    }

    private static Tags tags(Map<String, String> values) {
        validateTags(values);
        Tags tags = Tags.empty();
        for (var entry : values.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            tags = tags.and(entry.getKey(), entry.getValue());
        }
        return tags;
    }

    private static String canonicalTags(Map<String, String> values) {
        validateTags(values);
        return values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + '=' + entry.getValue())
                .reduce((left, right) -> left + ',' + right)
                .orElse("none");
    }

    private static void validateTags(Map<String, String> values) {
        if (values == null || values.size() > 4) {
            throw new IllegalArgumentException("metric tags must be bounded");
        }
        for (var entry : values.entrySet()) {
            if (!entry.getKey().matches("[a-z][a-z0-9_]{0,31}")
                    || !entry.getValue().matches("[a-z0-9_]{1,32}")) {
                throw new IllegalArgumentException("metric tag is invalid");
            }
            if (entry.getKey().matches(".*(query|user|account|session|jwt|post|document|trace|correlation).*")) {
                throw new IllegalArgumentException("high-cardinality metric tag is prohibited");
            }
        }
    }
}
