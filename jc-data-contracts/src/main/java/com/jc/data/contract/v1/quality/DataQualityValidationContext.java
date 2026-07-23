package com.jc.data.contract.v1.quality;

import com.jc.data.contract.v1.projection.ProjectionDefinition;
import com.jc.data.contract.v1.projection.ProjectionLineage;
import com.jc.data.contract.v1.projection.ProjectionRecord;
import com.jc.data.contract.v1.projection.ProjectionSnapshot;
import com.jc.data.contract.v1.projection.ProjectionSourceEvent;
import com.jc.data.contract.v1.projection.SourceCheckpoint;
import java.util.List;
import java.util.Objects;

public record DataQualityValidationContext(
        DataQualityValidationDefinition definition,
        DataQualityPolicy qualityPolicy,
        ProjectionDefinition projectionDefinition,
        SourceCheckpoint checkpoint,
        List<ProjectionSourceEvent> sourceEvents,
        List<ProjectionRecord> projectionRecords,
        ProjectionSnapshot snapshot,
        List<ProjectionLineage> lineage,
        List<IdentityBindingEvidence> identityBindings,
        List<P2ExposureEvidence> exposureEvidence) {
    public DataQualityValidationContext {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(qualityPolicy, "qualityPolicy");
        Objects.requireNonNull(projectionDefinition, "projectionDefinition");
        Objects.requireNonNull(checkpoint, "checkpoint");
        sourceEvents = List.copyOf(Objects.requireNonNull(sourceEvents, "sourceEvents"));
        projectionRecords = List.copyOf(Objects.requireNonNull(projectionRecords, "projectionRecords"));
        Objects.requireNonNull(snapshot, "snapshot");
        lineage = List.copyOf(Objects.requireNonNull(lineage, "lineage"));
        identityBindings = List.copyOf(Objects.requireNonNull(identityBindings, "identityBindings"));
        exposureEvidence = List.copyOf(Objects.requireNonNull(exposureEvidence, "exposureEvidence"));
        if (!DataQualityPolicy.VERSION.equals(definition.qualityPolicyVersion())
                || !qualityPolicy.qualityPolicyVersion().equals(definition.qualityPolicyVersion())) {
            throw new IllegalArgumentException("unsupported quality policy version");
        }
        if (!definition.snapshotRef().equals(snapshot.snapshotRef())) {
            throw new IllegalArgumentException("definition snapshot mismatch");
        }
    }
}
