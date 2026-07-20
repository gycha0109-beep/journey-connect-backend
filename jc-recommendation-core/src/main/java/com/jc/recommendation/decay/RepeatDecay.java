package com.jc.recommendation.decay;

import com.jc.recommendation.model.event.EventType;
import com.jc.recommendation.policy.RepeatDecayPolicy;

import java.util.Objects;

public final class RepeatDecay {
    private RepeatDecay() {
    }

    public static RepeatDecayResult apply(
            double baseDelta,
            int occurrenceNumber,
            EventType eventType,
            RepeatDecayPolicy policy
    ) {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(policy, "policy");
        if (!Double.isFinite(baseDelta)) {
            throw new IllegalArgumentException("baseDelta must be finite");
        }
        if (occurrenceNumber < 1) {
            throw new IllegalArgumentException("occurrenceNumber must be an integer greater than or equal to 1");
        }
        if (!policy.supportedEventTypes().contains(eventType)) {
            throw new IllegalArgumentException("Repeat decay is not supported for event type: " + eventType.wireValue());
        }

        double multiplier = occurrenceNumber == 1
                ? policy.firstMultiplier()
                : occurrenceNumber == 2
                ? policy.secondMultiplier()
                : policy.thirdAndLaterMultiplier();
        return new RepeatDecayResult(multiplier, baseDelta * multiplier);
    }

    public record RepeatDecayResult(double multiplier, double decayedDelta) {
    }
}
