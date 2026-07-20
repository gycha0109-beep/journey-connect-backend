package com.jc.backend.intelligence.compat.recommendation;

import com.jc.intelligence.contract.v1.identity.SnapshotRef;
import com.jc.intelligence.contract.v1.version.FeatureDefinitionVersion;
import com.jc.intelligence.contract.v1.version.PolicyVersion;
import java.time.Instant;
import java.util.List;

public final class RecommendationP1ProfileCompatibilityAdapterV1 {
    public RecommendationP1ProfileCompatibilityViewV1 adapt(
            RecommendationP1ProfileCompatibilityInputV1 source) {
        java.util.Objects.requireNonNull(source, "source");
        return new RecommendationP1ProfileCompatibilityViewV1(
                new SnapshotRef(source.profileSnapshotId()),
                source.userId(),
                source.segment(),
                new PolicyVersion(source.profilePolicyVersion()),
                new FeatureDefinitionVersion(source.featureVocabularyVersion()),
                source.referenceTime(),
                List.copyOf(source.signalIds()),
                source.profileFingerprint(),
                "recommendation_p1_profile_snapshot",
                false);
    }

    public record RecommendationP1ProfileCompatibilityInputV1(
            String profileSnapshotId,
            long userId,
            String segment,
            String profilePolicyVersion,
            String featureVocabularyVersion,
            Instant referenceTime,
            List<String> signalIds,
            String profileFingerprint) {
        public RecommendationP1ProfileCompatibilityInputV1 {
            java.util.Objects.requireNonNull(profileSnapshotId, "profileSnapshotId");
            if (userId <= 0L) {
                throw new IllegalArgumentException("userId must be positive");
            }
            java.util.Objects.requireNonNull(segment, "segment");
            if (!List.of("empty", "explicit_only", "emerging", "established").contains(segment)) {
                throw new IllegalArgumentException("unknown protected P1 segment");
            }
            java.util.Objects.requireNonNull(profilePolicyVersion, "profilePolicyVersion");
            java.util.Objects.requireNonNull(featureVocabularyVersion, "featureVocabularyVersion");
            java.util.Objects.requireNonNull(referenceTime, "referenceTime");
            signalIds = List.copyOf(java.util.Objects.requireNonNull(signalIds, "signalIds"));
            java.util.List<String> sortedUnique = signalIds.stream().distinct().sorted().toList();
            if (!signalIds.equals(sortedUnique)) {
                throw new IllegalArgumentException("P1 signalIds must preserve sorted unique order");
            }
            if (!profileFingerprint.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("profileFingerprint must be lowercase SHA-256");
            }
        }
    }

    public record RecommendationP1ProfileCompatibilityViewV1(
            SnapshotRef profileSnapshotRef,
            long userId,
            String segment,
            PolicyVersion profilePolicyVersion,
            FeatureDefinitionVersion featureVocabularyVersion,
            Instant referenceTime,
            List<String> signalIds,
            String profileFingerprint,
            String authoritativeSource,
            boolean dataShadowProjectionAuthoritative) {
        public RecommendationP1ProfileCompatibilityViewV1 {
            signalIds = List.copyOf(signalIds);
            if (dataShadowProjectionAuthoritative) {
                throw new IllegalArgumentException("IP-1 must not authorize the Data P1 shadow projection");
            }
        }
    }
}
