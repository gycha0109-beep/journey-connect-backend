package com.jc.data.contract.v1.projection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ProjectionLineage(
        String snapshotRef,
        String projectionRecordRef,
        String sourceEventRef,
        String sourceFingerprint,
        String adapterEvidenceRef,
        String sourceCheckpointRef,
        String projectionPolicyVersion,
        String mappingPolicyVersion,
        String lineageEntryFingerprint) {

    public ProjectionLineage {
        snapshotRef = ProjectionEngineSupport.requireReference(snapshotRef, "snapshotRef");
        projectionRecordRef = ProjectionEngineSupport.requireReference(
                projectionRecordRef, "projectionRecordRef");
        sourceEventRef = ProjectionEngineSupport.requireReference(sourceEventRef, "sourceEventRef");
        sourceFingerprint = ProjectionEngineSupport.requireFingerprint(sourceFingerprint, "sourceFingerprint");
        if (adapterEvidenceRef != null) {
            adapterEvidenceRef = ProjectionEngineSupport.requireReference(
                    adapterEvidenceRef, "adapterEvidenceRef");
            mappingPolicyVersion = ProjectionEngineSupport.requireVersion(
                    mappingPolicyVersion, "mappingPolicyVersion");
        } else if (mappingPolicyVersion != null) {
            throw new IllegalArgumentException("mappingPolicyVersion requires adapterEvidenceRef");
        }
        sourceCheckpointRef = ProjectionEngineSupport.requireReference(
                sourceCheckpointRef, "sourceCheckpointRef");
        projectionPolicyVersion = ProjectionEngineSupport.requireVersion(
                projectionPolicyVersion, "projectionPolicyVersion");
        lineageEntryFingerprint = ProjectionEngineSupport.requireFingerprint(
                lineageEntryFingerprint, "lineageEntryFingerprint");
    }

    public Map<String, Object> canonicalFields() {
        LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
        fields.put("projectionRecordRef", projectionRecordRef);
        fields.put("sourceEventRef", sourceEventRef);
        fields.put("sourceFingerprint", sourceFingerprint);
        fields.put("adapterEvidenceRef", adapterEvidenceRef);
        fields.put("sourceCheckpointRef", sourceCheckpointRef);
        fields.put("projectionPolicyVersion", projectionPolicyVersion);
        fields.put("mappingPolicyVersion", mappingPolicyVersion);
        return Collections.unmodifiableMap(fields);
    }
}
