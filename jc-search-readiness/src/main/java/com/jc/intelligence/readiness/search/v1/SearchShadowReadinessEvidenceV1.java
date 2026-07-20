package com.jc.intelligence.readiness.search.v1;

import com.jc.intelligence.contract.v1.version.ProducerBuildId;
import java.time.Instant;
import java.util.Objects;

public record SearchShadowReadinessEvidenceV1(
        String assessmentFingerprint,
        SearchShadowReadinessDecision proposalDecision,
        SearchShadowReadinessDecision activationDecision,
        long activationBlockerCount,
        long cutoverBlockerCount,
        boolean disabledModeEquivalent,
        Instant referenceTime,
        ProducerBuildId producerBuildId,
        SearchShadowReadinessAuthorityV1 authority) {
    public SearchShadowReadinessEvidenceV1 {
        if (assessmentFingerprint == null || !assessmentFingerprint.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("assessmentFingerprint must be SHA-256");
        Objects.requireNonNull(proposalDecision, "proposalDecision");
        Objects.requireNonNull(activationDecision, "activationDecision");
        if (activationBlockerCount < 0 || cutoverBlockerCount < 0) throw new IllegalArgumentException("blocker counts cannot be negative");
        Objects.requireNonNull(referenceTime, "referenceTime");
        Objects.requireNonNull(producerBuildId, "producerBuildId");
        if (!SearchShadowReadinessAuthorityV1.legacyOnly().equals(authority)) throw new IllegalArgumentException("evidence has no production authority");
    }
}
