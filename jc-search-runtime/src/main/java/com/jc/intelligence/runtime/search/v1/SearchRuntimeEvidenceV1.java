package com.jc.intelligence.runtime.search.v1;

import com.jc.intelligence.contract.v1.identity.SnapshotRef;
import com.jc.intelligence.contract.v1.version.PolicyVersion;
import com.jc.intelligence.contract.v1.version.ProducerBuildId;
import com.jc.intelligence.contract.v1.version.SchemaVersion;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record SearchRuntimeEvidenceV1(
        SchemaVersion runtimeVersion,
        SchemaVersion retrievalStrategyVersion,
        PolicyVersion rankingPolicyVersion,
        SchemaVersion queryNormalizationVersion,
        Instant referenceTime,
        String inputFingerprint,
        int candidateCount,
        int filteredCount,
        int eligibleCount,
        int rankedCount,
        int resultCount,
        int rejectedCount,
        SearchRuntimeFallbackCode fallbackCode,
        SearchRuntimeFailureCode failureCode,
        SnapshotRef snapshotId,
        ProducerBuildId producerBuildId,
        List<SearchRuntimeStageEvidenceV1> stages) {
    public SearchRuntimeEvidenceV1 {
        Objects.requireNonNull(runtimeVersion, "runtimeVersion");
        Objects.requireNonNull(retrievalStrategyVersion, "retrievalStrategyVersion");
        Objects.requireNonNull(rankingPolicyVersion, "rankingPolicyVersion");
        Objects.requireNonNull(queryNormalizationVersion, "queryNormalizationVersion");
        Objects.requireNonNull(referenceTime, "referenceTime");
        if (inputFingerprint == null || !inputFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("inputFingerprint must be lowercase SHA-256");
        }
        for (int count : new int[]{candidateCount, filteredCount, eligibleCount, rankedCount, resultCount, rejectedCount}) {
            if (count < 0) throw new IllegalArgumentException("evidence counts must be nonnegative");
        }
        if (filteredCount > candidateCount || eligibleCount > filteredCount || rankedCount > eligibleCount
                || resultCount > rankedCount || rejectedCount != candidateCount - eligibleCount) {
            throw new IllegalArgumentException("runtime evidence counts are inconsistent");
        }
        Objects.requireNonNull(producerBuildId, "producerBuildId");
        stages = List.copyOf(Objects.requireNonNull(stages, "stages"));
    }
}
