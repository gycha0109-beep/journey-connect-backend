package com.jc.recommendation.foundation;

import com.jc.recommendation.decay.RepeatDecay;
import com.jc.recommendation.decay.TimeDecay;
import com.jc.recommendation.limit.CandidateLimiter;
import com.jc.recommendation.model.event.EventType;
import com.jc.recommendation.policy.CandidateLimitPolicies;
import com.jc.recommendation.policy.FoundationPolicies;
import com.jc.recommendation.saturation.ScoreSaturation;
import com.jc.recommendation.vocabulary.FeatureVocabularyV1;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CoreFoundationOracle {
    private final List<String> lines = new ArrayList<>();

    public static void main(String[] args) {
        new CoreFoundationOracle().run();
    }

    private void run() {
        policyLines();
        eventWeightLines();
        featureLines();
        sourcePriorityLine();
        coldStartLines();
        candidateLimitLines();
        repeatDecayLines();
        timeDecayLines();
        saturationLines();
        try {
            System.out.write((String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write foundation oracle", exception);
        }
    }

    private void policyLines() {
        var policies = List.of(
                FoundationPolicies.EVENT_WEIGHT_V1,
                FoundationPolicies.REPEAT_DECAY_V1,
                FoundationPolicies.TIME_DECAY_V1,
                FoundationPolicies.SATURATION_V1,
                CandidateLimitPolicies.V1,
                FoundationPolicies.COLD_START_V1,
                FoundationPolicies.SOURCE_PRIORITY_V1
        );
        policies.forEach(policy -> emit("POLICY", policy.policyVersion(), policy.effectiveFrom().toString()));
    }

    private void eventWeightLines() {
        for (EventType eventType : EventType.values()) {
            Double weight = FoundationPolicies.EVENT_WEIGHT_V1.weights().get(eventType);
            emit("EVENT_WEIGHT", eventType.wireValue(), weight == null ? "null" : bits(weight));
        }
    }

    private void featureLines() {
        FeatureVocabularyV1.getAllFeatures().forEach(feature -> emit(
                "FEATURE",
                feature.id(),
                feature.group().wireValue(),
                feature.key(),
                feature.displayName(),
                feature.status().wireValue()
        ));
    }

    private void sourcePriorityLine() {
        List<String> values = new ArrayList<>();
        values.add("SOURCE_PRIORITY");
        FoundationPolicies.SOURCE_PRIORITY_V1.priority().forEach(item -> values.add(item.wireValue()));
        emit(values.toArray(String[]::new));
    }

    private void coldStartLines() {
        var explicit = FoundationPolicies.COLD_START_V1.explicitPreference();
        emit(
                "COLD_EXPLICIT",
                bits(explicit.explicitPreferenceMatch()),
                bits(explicit.freshness()),
                bits(explicit.popularity()),
                bits(explicit.explorationDiversity())
        );
        var empty = FoundationPolicies.COLD_START_V1.emptyProfile();
        emit("COLD_EMPTY", bits(empty.freshness()), bits(empty.popularity()), bits(empty.explorationDiversity()));
    }

    private void candidateLimitLines() {
        List<Integer> candidates = new ArrayList<>();
        for (int index = 0; index < 150; index++) {
            candidates.add(index);
        }
        emit("LIMIT", joinIntegers(CandidateLimiter.limitCandidates(candidates, CandidateLimitPolicies.V1)));
        emit("LIMIT_7", joinIntegers(CandidateLimiter.limitCandidates(candidates.subList(0, 10), CandidateLimitPolicies.V1, 7)));
    }

    private void repeatDecayLines() {
        for (int occurrence : new int[]{1, 2, 3, 100}) {
            var result = RepeatDecay.apply(1, occurrence, EventType.VIEW, FoundationPolicies.REPEAT_DECAY_V1);
            emit("REPEAT", Integer.toString(occurrence), bits(result.multiplier()), bits(result.decayedDelta()));
        }
    }

    private void timeDecayLines() {
        Instant referenceTime = Instant.parse("2026-07-01T00:00:00Z");
        for (String occurredAtText : List.of(
                "2026-06-30T00:00:00Z",
                "2026-06-24T00:00:00Z",
                "2026-06-21T00:00:00Z",
                "2026-06-01T00:00:00Z",
                "2026-05-02T00:00:00Z",
                "2026-04-02T00:00:00Z",
                "2026-03-03T00:00:00Z"
        )) {
            var result = TimeDecay.apply(Instant.parse(occurredAtText), referenceTime, FoundationPolicies.TIME_DECAY_V1);
            emit(
                    "TIME",
                    occurredAtText,
                    bits(result.multiplier()),
                    Long.toString(result.elapsedMilliseconds()),
                    bits(result.elapsedDays()),
                    result.bucketId()
            );
        }
    }

    private void saturationLines() {
        for (double raw : new double[]{-5, 0, 1, 5, 10, 95, 100}) {
            emit("SATURATION", integerLabel(raw), bits(ScoreSaturation.saturate(raw, FoundationPolicies.SATURATION_V1)));
        }
    }

    private void emit(String... parts) {
        lines.add(String.join("\t", parts));
    }

    private static String joinIntegers(List<Integer> values) {
        return values.stream().map(String::valueOf).reduce((left, right) -> left + "," + right).orElse("");
    }

    private static String bits(double value) {
        return String.format(Locale.ROOT, "%016x", Double.doubleToRawLongBits(value));
    }

    private static String integerLabel(double value) {
        return Long.toString((long) value);
    }
}
