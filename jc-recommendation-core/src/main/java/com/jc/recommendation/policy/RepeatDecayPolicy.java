package com.jc.recommendation.policy;

import com.jc.recommendation.model.event.EventType;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record RepeatDecayPolicy(
        String policyVersion,
        Instant effectiveFrom,
        List<EventType> supportedEventTypes,
        double firstMultiplier,
        double secondMultiplier,
        double thirdAndLaterMultiplier
) implements VersionedPolicy {
    public RepeatDecayPolicy {
        Objects.requireNonNull(policyVersion, "policyVersion");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        supportedEventTypes = List.copyOf(supportedEventTypes);
    }
}
