package com.jc.recommendation.p1.ranking;

import com.jc.recommendation.p1.policy.P1PolicySelection;
import com.jc.recommendation.p1.profile.BehaviorProfileSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record P1RankingInput(
        String rankingSnapshotId,
        String userId,
        String contextId,
        Instant referenceTime,
        BehaviorProfileSnapshot profile,
        P1PolicySelection policySelection,
        List<P1CandidateInput> candidates) {

    public P1RankingInput {
        Objects.requireNonNull(rankingSnapshotId, "rankingSnapshotId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(contextId, "contextId");
        Objects.requireNonNull(referenceTime, "referenceTime");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(policySelection, "policySelection");
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        if (!profile.userId().equals(userId)) {
            throw new IllegalArgumentException("profile user does not match ranking user");
        }
        if (!profile.referenceTime().equals(referenceTime)) {
            throw new IllegalArgumentException("profile reference time does not match ranking reference time");
        }
        if (profile.segment() != policySelection.policyBundle().segment()) {
            throw new IllegalArgumentException("profile segment does not match selected policy");
        }
        if (!profile.profilePolicyVersion().equals(policySelection.policyBundle().profilePolicyVersion())
                || !profile.featureVocabularyVersion().equals(
                        policySelection.policyBundle().featureVocabularyVersion())) {
            throw new IllegalArgumentException("profile versions do not match selected policy");
        }
        if (policySelection.assignment()
                != com.jc.recommendation.p1.policy.P1ExperimentAssignment.TREATMENT) {
            throw new IllegalArgumentException("P1 ranking requires treatment assignment");
        }
        if (referenceTime.isBefore(policySelection.policyBundle().scorePolicy().effectiveFrom())
                || referenceTime.isBefore(policySelection.policyBundle().diversityPolicy().effectiveFrom())) {
            throw new IllegalArgumentException("selected P1 policy is not effective at referenceTime");
        }
    }
}
