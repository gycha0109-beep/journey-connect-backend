package com.jc.recommendation.policy;

import java.time.Instant;
import java.util.Objects;

public record RecommendationTracePolicy(
        String policyVersion, Instant effectiveFrom, String eventSchemaVersion,
        String expectedRankingPolicyVersion, String expectedCursorVersion, int maximumPageCandidateCount,
        String fingerprintAlgorithm, String idempotencyMode, String timestampSource, String exposureSemantic,
        String signedZeroEncoding, String rawCursorLogging, String rawSnapshotPayloadLogging, String freeTextLogging
) implements VersionedPolicy {
    public RecommendationTracePolicy {
        Objects.requireNonNull(policyVersion, "policyVersion");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
    }
}
