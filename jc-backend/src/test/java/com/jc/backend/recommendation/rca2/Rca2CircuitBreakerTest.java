package com.jc.backend.recommendation.rca2;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class Rca2CircuitBreakerTest {
    @Test void minimumSamplesFailureTimeoutHalfOpenAndLaneIsolation() {
        AtomicLong ticker = new AtomicLong();
        var p1 = new Rca2CircuitBreaker(ticker::get);
        var p2 = new Rca2CircuitBreaker(ticker::get);
        for (int i = 0; i < 19; i++) p1.failure(false);
        assertThat(p1.state()).isEqualTo(Rca2RuntimeContracts.BreakerState.CLOSED);
        p1.failure(false);
        assertThat(p1.state()).isEqualTo(Rca2RuntimeContracts.BreakerState.OPEN);
        assertThat(p1.permit()).isFalse();
        assertThat(p2.state()).isEqualTo(Rca2RuntimeContracts.BreakerState.CLOSED);
        ticker.addAndGet(Duration.ofSeconds(60).toNanos());
        assertThat(p1.state()).isEqualTo(Rca2RuntimeContracts.BreakerState.HALF_OPEN);
        assertThat(p1.permit()).isTrue(); p1.success();
        assertThat(p1.permit()).isTrue(); p1.success();
        assertThat(p1.state()).isEqualTo(Rca2RuntimeContracts.BreakerState.CLOSED);
        for (int i = 0; i < 20; i++) p2.failure(true);
        assertThat(p2.state()).isEqualTo(Rca2RuntimeContracts.BreakerState.OPEN);
        ticker.addAndGet(Duration.ofSeconds(60).toNanos());
        assertThat(p2.permit()).isTrue(); p2.failure(false);
        assertThat(p2.state()).isEqualTo(Rca2RuntimeContracts.BreakerState.OPEN);
    }
}
