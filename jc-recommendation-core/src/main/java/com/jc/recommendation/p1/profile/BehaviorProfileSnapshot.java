package com.jc.recommendation.p1.profile;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record BehaviorProfileSnapshot(
        String userId,
        Instant referenceTime,
        String profilePolicyVersion,
        String featureVocabularyVersion,
        UserProfileSegment segment,
        int explicitPreferenceCount,
        int inputEventCount,
        int acceptedEventCount,
        int ignoredEventCount,
        int duplicateEventCount,
        double acceptedBehaviorWeight,
        List<P1FeatureSignal> signals,
        String fingerprint) {

    public BehaviorProfileSnapshot {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(referenceTime, "referenceTime");
        Objects.requireNonNull(profilePolicyVersion, "profilePolicyVersion");
        Objects.requireNonNull(featureVocabularyVersion, "featureVocabularyVersion");
        Objects.requireNonNull(segment, "segment");
        signals = List.copyOf(Objects.requireNonNull(signals, "signals"));
        Objects.requireNonNull(fingerprint, "fingerprint");
        if (explicitPreferenceCount < 0 || inputEventCount < 0 || acceptedEventCount < 0
                || ignoredEventCount < 0 || duplicateEventCount < 0) {
            throw new IllegalArgumentException("profile counts must be nonnegative");
        }
        if (acceptedEventCount + ignoredEventCount + duplicateEventCount != inputEventCount) {
            throw new IllegalArgumentException("profile event partition must be exact");
        }
        if (!Double.isFinite(acceptedBehaviorWeight) || acceptedBehaviorWeight < 0.0d) {
            throw new IllegalArgumentException("acceptedBehaviorWeight must be finite and nonnegative");
        }
        java.util.HashSet<String> featureIds = new java.util.HashSet<>();
        String previousFeatureId = null;
        for (P1FeatureSignal signal : signals) {
            if (!featureIds.add(signal.featureId())
                    || (previousFeatureId != null && previousFeatureId.compareTo(signal.featureId()) >= 0)) {
                throw new IllegalArgumentException("profile signals must be unique and sorted by feature ID");
            }
            previousFeatureId = signal.featureId();
        }
        if (!fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("fingerprint must be lowercase SHA-256 hex");
        }
    }
}
