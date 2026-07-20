package com.jc.backend.intelligence.compat.recommendation;

import com.jc.backend.recommendation.persistence.RecommendationStorageTypes.RunMode;
import com.jc.backend.recommendation.persistence.RecommendationStorageTypes.RunStatus;
import com.jc.backend.recommendation.persistence.RecommendationStorageTypes.Surface;
import com.jc.intelligence.contract.v1.authority.ExposureSourceId;
import com.jc.intelligence.contract.v1.replay.ReplayEvidenceDescriptorV1;
import com.jc.intelligence.contract.v1.run.ExperimentRefV1;
import java.time.Instant;

public record RecommendationRunCompatibilityInputV1(
        String runId,
        String requestId,
        String correlationId,
        RunMode runMode,
        RunStatus runStatus,
        long userId,
        String sessionId,
        String contextId,
        Surface surface,
        Instant referenceTime,
        Instant startedAt,
        Instant completedAt,
        String rankingSnapshotId,
        String resultSnapshotId,
        String rankingPolicyVersion,
        String featureDefinitionVersion,
        String coreBuildId,
        String fallbackReason,
        String failureCode,
        ReplayEvidenceDescriptorV1 replayEvidence,
        ExperimentRefV1 experimentRef,
        ExposureSourceId exposureSourceId) {

    public RecommendationRunCompatibilityInputV1 {
        java.util.Objects.requireNonNull(runId, "runId");
        java.util.Objects.requireNonNull(requestId, "requestId");
        java.util.Objects.requireNonNull(correlationId, "correlationId");
        java.util.Objects.requireNonNull(runMode, "runMode");
        java.util.Objects.requireNonNull(runStatus, "runStatus");
        if (userId <= 0L) {
            throw new IllegalArgumentException("userId must be positive");
        }
        java.util.Objects.requireNonNull(sessionId, "sessionId");
        java.util.Objects.requireNonNull(contextId, "contextId");
        java.util.Objects.requireNonNull(surface, "surface");
        java.util.Objects.requireNonNull(referenceTime, "referenceTime");
        java.util.Objects.requireNonNull(startedAt, "startedAt");
        java.util.Objects.requireNonNull(completedAt, "completedAt");
        java.util.Objects.requireNonNull(rankingSnapshotId, "rankingSnapshotId");
        java.util.Objects.requireNonNull(resultSnapshotId, "resultSnapshotId");
        java.util.Objects.requireNonNull(rankingPolicyVersion, "rankingPolicyVersion");
        java.util.Objects.requireNonNull(coreBuildId, "coreBuildId");
        java.util.Objects.requireNonNull(replayEvidence, "replayEvidence");
        if (runStatus == RunStatus.FALLBACK && (fallbackReason == null || fallbackReason.isBlank())) {
            throw new IllegalArgumentException("fallback run requires fallbackReason");
        }
        if (runStatus != RunStatus.FALLBACK && fallbackReason != null) {
            throw new IllegalArgumentException("non-fallback run must not have fallbackReason");
        }
        if (runStatus == RunStatus.FAILED && (failureCode == null || failureCode.isBlank())) {
            throw new IllegalArgumentException("failed run requires failureCode");
        }
        if (runStatus != RunStatus.FAILED && failureCode != null) {
            throw new IllegalArgumentException("non-failed run must not have failureCode");
        }
    }
}
