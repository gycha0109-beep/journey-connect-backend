package com.jc.recommendation.decay;

import com.jc.recommendation.policy.TimeDecayBucket;
import com.jc.recommendation.policy.TimeDecayPolicy;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class TimeDecay {
    private static final double MILLISECONDS_PER_DAY = 86_400_000.0;

    private TimeDecay() {
    }

    public static TimeDecayResult apply(Instant occurredAt, Instant referenceTime, TimeDecayPolicy policy) {
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(referenceTime, "referenceTime");
        Objects.requireNonNull(policy, "policy");

        long elapsedMilliseconds = Duration.between(occurredAt, referenceTime).toMillis();
        if (elapsedMilliseconds < 0) {
            throw new IllegalArgumentException("occurredAt cannot be later than referenceTime");
        }
        double elapsedDays = elapsedMilliseconds / MILLISECONDS_PER_DAY;
        TimeDecayBucket bucket = policy.buckets().stream()
                .filter(candidate -> candidate.maxElapsedDays() == null
                        || elapsedDays <= candidate.maxElapsedDays())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No matching time decay bucket"));

        return new TimeDecayResult(
                bucket.multiplier(),
                elapsedMilliseconds,
                elapsedDays,
                bucket.id()
        );
    }

    public record TimeDecayResult(
            double multiplier,
            long elapsedMilliseconds,
            double elapsedDays,
            String bucketId
    ) {
    }
}
