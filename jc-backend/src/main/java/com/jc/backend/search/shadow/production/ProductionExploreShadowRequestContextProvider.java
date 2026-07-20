package com.jc.backend.search.shadow.production;

import com.jc.backend.search.shadow.ExploreShadowRequestContext;
import com.jc.backend.search.shadow.ExploreShadowRequestContextProvider;
import com.jc.intelligence.contract.v1.version.ProducerBuildId;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class ProductionExploreShadowRequestContextProvider implements ExploreShadowRequestContextProvider {
    private static final ProducerBuildId BUILD = new ProducerBuildId("ip12-production-shadow-wiring");
    private final Clock clock;

    public ProductionExploreShadowRequestContextProvider(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public ExploreShadowRequestContext current() {
        Instant now = clock.instant();
        String opaque = UUID.randomUUID().toString();
        return new ExploreShadowRequestContext(
                "request:" + opaque,
                "correlation:" + opaque,
                null,
                now,
                now,
                BUILD);
    }
}
