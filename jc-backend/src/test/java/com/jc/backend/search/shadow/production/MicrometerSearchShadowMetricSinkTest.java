package com.jc.backend.search.shadow.production;

import static org.assertj.core.api.Assertions.assertThat;

import com.jc.intelligence.production.search.v1.SearchShadowMetricName;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MicrometerSearchShadowMetricSinkTest {
    @Test
    void bindsCountersTimersAndBoundedGauges() {
        var registry = new SimpleMeterRegistry();
        var sink = new MicrometerSearchShadowMetricSink(registry);
        sink.increment(SearchShadowMetricName.DISPATCHED, Map.of("outcome", "accepted"));
        sink.recordDuration(SearchShadowMetricName.RUNTIME_LATENCY, Duration.ofMillis(12),
                Map.of("outcome", "compared"));
        sink.recordGauge(SearchShadowMetricName.QUEUE_DEPTH, 2, Map.of("environment", "prod"));

        assertThat(registry.get("journey.search.shadow.dispatched").counter().count()).isEqualTo(1.0d);
        assertThat(registry.get("journey.search.shadow.runtime.latency").timer().count()).isEqualTo(1L);
        assertThat(registry.get("journey.search.shadow.queue.depth").gauge().value()).isEqualTo(2.0d);
    }

    @Test
    void prohibitedHighCardinalityTagsAreDroppedWithoutPropagation() {
        var registry = new SimpleMeterRegistry();
        var sink = new MicrometerSearchShadowMetricSink(registry);
        sink.increment(SearchShadowMetricName.DISPATCHED, Map.of("account_hash", "forbidden"));
        assertThat(registry.find("journey.search.shadow.dispatched").counter()).isNull();
    }
}
