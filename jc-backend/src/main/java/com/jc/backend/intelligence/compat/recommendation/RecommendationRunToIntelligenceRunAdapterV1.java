package com.jc.backend.intelligence.compat.recommendation;

import com.jc.backend.recommendation.persistence.RecommendationStorageTypes.RunStatus;
import com.jc.intelligence.contract.v1.identity.RunRef;
import com.jc.intelligence.contract.v1.identity.SnapshotRef;
import com.jc.intelligence.contract.v1.identity.SubjectRef;
import com.jc.intelligence.contract.v1.run.IntelligenceRunStatus;
import com.jc.intelligence.contract.v1.run.IntelligenceRunType;
import com.jc.intelligence.contract.v1.run.IntelligenceRunV1;
import com.jc.intelligence.contract.v1.version.FeatureDefinitionVersion;
import com.jc.intelligence.contract.v1.version.IntelligenceContractIds;
import com.jc.intelligence.contract.v1.version.PolicyVersion;
import com.jc.intelligence.contract.v1.version.ProducerBuildId;

public final class RecommendationRunToIntelligenceRunAdapterV1 {
    public IntelligenceRunV1 adapt(RecommendationRunCompatibilityInputV1 source) {
        java.util.Objects.requireNonNull(source, "source");
        return new IntelligenceRunV1(
                IntelligenceContractIds.INTELLIGENCE_RUN,
                new RunRef(source.runId()),
                IntelligenceRunType.RECOMMENDATION,
                status(source.runStatus()),
                source.requestId(),
                source.correlationId(),
                source.runMode().value(),
                source.surface().value(),
                null,
                SubjectRef.legacyUser(source.userId()),
                new SnapshotRef(source.rankingSnapshotId()),
                source.runStatus() == RunStatus.FAILED
                        ? null : new SnapshotRef(source.resultSnapshotId()),
                new PolicyVersion(source.rankingPolicyVersion()),
                source.featureDefinitionVersion() == null
                        ? null : new FeatureDefinitionVersion(source.featureDefinitionVersion()),
                null,
                null,
                new ProducerBuildId(source.coreBuildId()),
                source.referenceTime(),
                source.startedAt(),
                source.completedAt(),
                source.replayEvidence().replayClass(),
                source.replayEvidence(),
                source.fallbackReason(),
                source.failureCode(),
                source.experimentRef(),
                source.exposureSourceId());
    }

    private static IntelligenceRunStatus status(RunStatus status) {
        return switch (status) {
            case SUCCEEDED -> IntelligenceRunStatus.SUCCEEDED;
            case FALLBACK -> IntelligenceRunStatus.FALLBACK;
            case FAILED -> IntelligenceRunStatus.FAILED;
        };
    }
}
