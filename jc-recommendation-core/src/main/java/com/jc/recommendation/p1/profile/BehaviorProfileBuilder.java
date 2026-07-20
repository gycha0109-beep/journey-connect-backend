package com.jc.recommendation.p1.profile;

import com.jc.recommendation.model.feature.PreferenceKind;
import com.jc.recommendation.p1.support.P1Canonical;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class BehaviorProfileBuilder {
    private static final double MILLIS_PER_DAY = 86_400_000.0d;

    public BehaviorProfileSnapshot build(BuildBehaviorProfileInput input) {
        Objects.requireNonNull(input, "input");
        if (input.events().size() > input.policy().maximumEvents()) {
            throw new IllegalArgumentException("behavior event count exceeds policy maximum");
        }

        if (input.referenceTime().isBefore(input.policy().effectiveFrom())) {
            throw new IllegalArgumentException("profile policy is not effective at referenceTime");
        }
        Map<String, ExplicitPreference> explicitByFeature = validateExplicit(input.explicitPreferences());
        List<BehaviorProfileEvent> orderedEvents = input.events().stream()
                .sorted(Comparator.comparing(BehaviorProfileEvent::occurredAt)
                        .thenComparing(BehaviorProfileEvent::eventId))
                .toList();

        Map<String, BehaviorProfileEvent> uniqueEvents = new LinkedHashMap<>();
        Map<String, Double> behaviorWeights = new TreeMap<>();
        int accepted = 0;
        int ignored = 0;
        int duplicates = 0;
        double totalAcceptedWeight = 0.0d;
        Instant earliest = input.referenceTime().minusSeconds((long) input.policy().lookbackDays() * 86_400L);

        for (BehaviorProfileEvent event : orderedEvents) {
            BehaviorProfileEvent previous = uniqueEvents.putIfAbsent(event.eventId(), event);
            if (previous != null) {
                if (!previous.equals(event)) {
                    throw new IllegalArgumentException("conflicting behavior event ID: " + event.eventId());
                }
                duplicates++;
                continue;
            }
            if (event.occurredAt().isAfter(input.referenceTime()) || event.occurredAt().isBefore(earliest)) {
                ignored++;
                continue;
            }
            double eventWeight = input.policy().eventWeights().getOrDefault(event.eventType(), 0.0d);
            if (eventWeight == 0.0d || event.featureIds().isEmpty()) {
                ignored++;
                continue;
            }
            double ageDays = Duration.between(event.occurredAt(), input.referenceTime()).toMillis()
                    / MILLIS_PER_DAY;
            double decayedWeight = eventWeight
                    * StrictMath.pow(0.5d, ageDays / input.policy().halfLifeDays());
            boolean acceptedAnyFeature = false;
            for (String featureId : event.featureIds()) {
                if (!P1FeatureVocabulary.isRegistered(featureId)) {
                    continue;
                }
                behaviorWeights.merge(featureId, decayedWeight, Double::sum);
                acceptedAnyFeature = true;
            }
            if (acceptedAnyFeature) {
                accepted++;
                totalAcceptedWeight += StrictMath.abs(decayedWeight);
            } else {
                ignored++;
            }
        }

        List<P1FeatureSignal> signals = mergeSignals(explicitByFeature, behaviorWeights, input.policy());
        UserProfileSegment segment = segment(
                explicitByFeature.size(), accepted, totalAcceptedWeight, input.policy());
        String fingerprint = P1Canonical.sha256(canonicalProfile(
                input,
                explicitByFeature.size(),
                segment,
                accepted,
                ignored,
                duplicates,
                totalAcceptedWeight,
                signals));
        return new BehaviorProfileSnapshot(
                input.userId(),
                input.referenceTime(),
                input.policy().policyVersion(),
                P1FeatureVocabulary.VERSION,
                segment,
                explicitByFeature.size(),
                input.events().size(),
                accepted,
                ignored,
                duplicates,
                totalAcceptedWeight,
                signals,
                fingerprint);
    }

    private static Map<String, ExplicitPreference> validateExplicit(List<ExplicitPreference> preferences) {
        Map<String, ExplicitPreference> result = new TreeMap<>();
        for (ExplicitPreference preference : preferences) {
            if (!P1FeatureVocabulary.isRegistered(preference.featureId())) {
                throw new IllegalArgumentException("unknown explicit preference feature: " + preference.featureId());
            }
            ExplicitPreference previous = result.putIfAbsent(preference.featureId(), preference);
            if (previous != null && !previous.equals(preference)) {
                throw new IllegalArgumentException("conflicting explicit preference: " + preference.featureId());
            }
        }
        return Map.copyOf(result);
    }

    private static List<P1FeatureSignal> mergeSignals(
            Map<String, ExplicitPreference> explicit,
            Map<String, Double> behavior,
            BehaviorProfilePolicy policy) {
        Map<String, Double> signedWeights = new TreeMap<>();
        Map<String, P1SignalSource> sources = new TreeMap<>();
        for (ExplicitPreference preference : explicit.values()) {
            double sign = preference.direction() == PreferenceKind.PREFER ? 1.0d : -1.0d;
            signedWeights.put(
                    preference.featureId(),
                    sign * preference.strength() * policy.explicitPreferenceWeight());
            sources.put(preference.featureId(), P1SignalSource.EXPLICIT);
        }
        for (Map.Entry<String, Double> entry : behavior.entrySet()) {
            signedWeights.merge(entry.getKey(), entry.getValue(), Double::sum);
            sources.merge(entry.getKey(), P1SignalSource.BEHAVIOR, (left, right) -> P1SignalSource.COMBINED);
        }

        List<P1FeatureSignal> result = new ArrayList<>();
        for (Map.Entry<String, Double> entry : signedWeights.entrySet()) {
            double signedWeight = entry.getValue();
            double strength = 1.0d - StrictMath.exp(-StrictMath.abs(signedWeight) / policy.saturationWeight());
            if (strength < policy.minimumSignalStrength()) {
                continue;
            }
            result.add(new P1FeatureSignal(
                    entry.getKey(),
                    signedWeight >= 0.0d ? PreferenceKind.PREFER : PreferenceKind.AVOID,
                    strength,
                    signedWeight,
                    sources.get(entry.getKey())));
        }
        return List.copyOf(result);
    }

    private static UserProfileSegment segment(
            int explicitCount,
            int acceptedEvents,
            double acceptedWeight,
            BehaviorProfilePolicy policy) {
        if (explicitCount == 0 && acceptedEvents == 0) {
            return UserProfileSegment.EMPTY;
        }
        if (acceptedEvents == 0) {
            return UserProfileSegment.EXPLICIT_ONLY;
        }
        if (acceptedEvents >= policy.establishedAcceptedEventThreshold()
                && acceptedWeight >= policy.establishedBehaviorWeightThreshold()) {
            return UserProfileSegment.ESTABLISHED;
        }
        return UserProfileSegment.EMERGING;
    }

    private static Map<String, Object> canonicalProfile(
            BuildBehaviorProfileInput input,
            int explicitPreferenceCount,
            UserProfileSegment segment,
            int accepted,
            int ignored,
            int duplicates,
            double totalAcceptedWeight,
            List<P1FeatureSignal> signals) {
        Map<String, Object> result = new TreeMap<>();
        result.put("domain", "journey-connect:p1-profile:v1");
        result.put("userId", input.userId());
        result.put("referenceTime", input.referenceTime().toString());
        result.put("profilePolicyVersion", input.policy().policyVersion());
        result.put("featureVocabularyVersion", P1FeatureVocabulary.VERSION);
        result.put("segment", segment.wireValue());
        result.put("explicitPreferenceCount", explicitPreferenceCount);
        result.put("inputEventCount", input.events().size());
        result.put("acceptedEventCount", accepted);
        result.put("ignoredEventCount", ignored);
        result.put("duplicateEventCount", duplicates);
        result.put("acceptedBehaviorWeight", totalAcceptedWeight);
        result.put("signals", signals.stream().map(BehaviorProfileBuilder::canonicalSignal).toList());
        return result;
    }

    private static Map<String, Object> canonicalSignal(P1FeatureSignal signal) {
        Map<String, Object> result = new TreeMap<>();
        result.put("featureId", signal.featureId());
        result.put("direction", signal.direction().wireValue());
        result.put("strength", signal.strength());
        result.put("signedWeight", signal.signedWeight());
        result.put("source", signal.source().wireValue());
        return result;
    }
}
