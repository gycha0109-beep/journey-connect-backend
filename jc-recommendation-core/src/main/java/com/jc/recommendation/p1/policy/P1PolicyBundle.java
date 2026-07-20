package com.jc.recommendation.p1.policy;

import com.jc.recommendation.model.context.ContextSurface;
import com.jc.recommendation.p1.profile.UserProfileSegment;
import java.util.Objects;

public record P1PolicyBundle(
        String policyBundleVersion,
        UserProfileSegment segment,
        ContextSurface surface,
        String profilePolicyVersion,
        String featureVocabularyVersion,
        String retrievalPolicyVersion,
        String lowExposurePolicyVersion,
        String explorationPolicyVersion,
        P1ScorePolicy scorePolicy,
        P1DiversityPolicy diversityPolicy) {

    public P1PolicyBundle {
        Objects.requireNonNull(policyBundleVersion, "policyBundleVersion");
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(profilePolicyVersion, "profilePolicyVersion");
        Objects.requireNonNull(featureVocabularyVersion, "featureVocabularyVersion");
        Objects.requireNonNull(retrievalPolicyVersion, "retrievalPolicyVersion");
        Objects.requireNonNull(lowExposurePolicyVersion, "lowExposurePolicyVersion");
        Objects.requireNonNull(explorationPolicyVersion, "explorationPolicyVersion");
        Objects.requireNonNull(scorePolicy, "scorePolicy");
        Objects.requireNonNull(diversityPolicy, "diversityPolicy");
    }
}
