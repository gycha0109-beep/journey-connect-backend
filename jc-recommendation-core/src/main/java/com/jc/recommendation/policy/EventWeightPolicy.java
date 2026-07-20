package com.jc.recommendation.policy;

import com.jc.recommendation.model.event.EventType;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public record EventWeightPolicy(
        String policyVersion,
        Instant effectiveFrom,
        Map<EventType, Double> weights
) implements VersionedPolicy {
    public EventWeightPolicy {
        Objects.requireNonNull(policyVersion, "policyVersion");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        Objects.requireNonNull(weights, "weights");
        EnumMap<EventType, Double> copy = new EnumMap<>(EventType.class);
        copy.putAll(weights);
        weights = Collections.unmodifiableMap(copy);
    }
}
