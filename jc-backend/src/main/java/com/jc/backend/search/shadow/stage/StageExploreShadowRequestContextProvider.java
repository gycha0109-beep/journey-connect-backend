package com.jc.backend.search.shadow.stage;

import com.jc.backend.search.shadow.ExploreShadowRequestContext;
import com.jc.backend.search.shadow.ExploreShadowRequestContextProvider;
import com.jc.intelligence.contract.v1.version.ProducerBuildId;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/** Supplies synthetic server-derived stage identifiers; no user/session identifier is copied. */
public final class StageExploreShadowRequestContextProvider implements ExploreShadowRequestContextProvider {
    private final Clock clock;
    private final AtomicLong sequence = new AtomicLong();

    public StageExploreShadowRequestContextProvider(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override public ExploreShadowRequestContext current() {
        long value = sequence.incrementAndGet();
        Instant now = clock.instant();
        return new ExploreShadowRequestContext(
                "request:ip10-stage-" + value,
                "correlation:ip10-stage-" + value,
                "session:ip10-stage",
                now,
                now,
                new ProducerBuildId("ip10-test-stage-shadow"));
    }
}
