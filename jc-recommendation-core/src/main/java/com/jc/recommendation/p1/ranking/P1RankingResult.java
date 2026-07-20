package com.jc.recommendation.p1.ranking;

import java.util.List;
import java.util.Objects;

public record P1RankingResult(
        String rankingSnapshotId,
        String userId,
        String contextId,
        String profileFingerprint,
        String policyBundleVersion,
        String scorePolicyVersion,
        String diversityPolicyVersion,
        String retrievalPolicyVersion,
        String lowExposurePolicyVersion,
        int inputCount,
        int rankedCount,
        int lowExposureBoostedCount,
        List<P1RankedCandidate> candidates,
        String fingerprint) {

    public P1RankingResult {
        Objects.requireNonNull(rankingSnapshotId, "rankingSnapshotId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(contextId, "contextId");
        Objects.requireNonNull(profileFingerprint, "profileFingerprint");
        Objects.requireNonNull(policyBundleVersion, "policyBundleVersion");
        Objects.requireNonNull(scorePolicyVersion, "scorePolicyVersion");
        Objects.requireNonNull(diversityPolicyVersion, "diversityPolicyVersion");
        Objects.requireNonNull(retrievalPolicyVersion, "retrievalPolicyVersion");
        Objects.requireNonNull(lowExposurePolicyVersion, "lowExposurePolicyVersion");
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        Objects.requireNonNull(fingerprint, "fingerprint");
        if (inputCount < 0 || rankedCount < 0 || lowExposureBoostedCount < 0
                || rankedCount != candidates.size() || rankedCount > inputCount
                || lowExposureBoostedCount > rankedCount) {
            throw new IllegalArgumentException("ranking counts are invalid");
        }
        java.util.HashSet<String> identities = new java.util.HashSet<>();
        for (int index = 0; index < candidates.size(); index++) {
            P1RankedCandidate candidate = candidates.get(index);
            if (candidate.absoluteRank() != index + 1
                    || !identities.add(candidate.entityType().wireValue() + ":" + candidate.entityId())) {
                throw new IllegalArgumentException("ranking candidates must be unique and contiguous");
            }
        }
        if (!fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("fingerprint must be lowercase SHA-256 hex");
        }
    }
}
