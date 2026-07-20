package com.jc.recommendation.p1.profile;

import com.jc.recommendation.model.event.EventType;
import com.jc.recommendation.policy.VersionedPolicy;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public record BehaviorProfilePolicy(
        String policyVersion,
        Instant effectiveFrom,
        int lookbackDays,
        int maximumEvents,
        double halfLifeDays,
        double saturationWeight,
        double minimumSignalStrength,
        double establishedBehaviorWeightThreshold,
        int establishedAcceptedEventThreshold,
        double explicitPreferenceWeight,
        Map<EventType, Double> eventWeights) implements VersionedPolicy {

    public BehaviorProfilePolicy {
        Objects.requireNonNull(policyVersion, "policyVersion");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        Objects.requireNonNull(eventWeights, "eventWeights");
        if (policyVersion.isBlank()) {
            throw new IllegalArgumentException("policyVersion must not be blank");
        }
        if (lookbackDays < 1 || maximumEvents < 1 || establishedAcceptedEventThreshold < 1) {
            throw new IllegalArgumentException("profile integer limits must be positive");
        }
        validatePositiveFinite(halfLifeDays, "halfLifeDays");
        validatePositiveFinite(saturationWeight, "saturationWeight");
        validateUnitInterval(minimumSignalStrength, "minimumSignalStrength");
        validatePositiveFinite(establishedBehaviorWeightThreshold, "establishedBehaviorWeightThreshold");
        validatePositiveFinite(explicitPreferenceWeight, "explicitPreferenceWeight");
        EnumMap<EventType, Double> copy = new EnumMap<>(EventType.class);
        for (Map.Entry<EventType, Double> entry : eventWeights.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "event type");
            Double weight = Objects.requireNonNull(entry.getValue(), "event weight");
            if (!Double.isFinite(weight)) {
                throw new IllegalArgumentException("event weights must be finite");
            }
            copy.put(entry.getKey(), weight);
        }
        eventWeights = Map.copyOf(copy);
    }

    private static void validatePositiveFinite(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0d) {
            throw new IllegalArgumentException(name + " must be positive and finite");
        }
    }

    private static void validateUnitInterval(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(name + " must be finite and within [0,1]");
        }
    }
}
