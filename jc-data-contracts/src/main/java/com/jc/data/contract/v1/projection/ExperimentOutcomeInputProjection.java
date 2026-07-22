package com.jc.data.contract.v1.projection;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ExperimentOutcomeInputProjection(
        String recordRef,
        String experimentRef,
        String experimentVersion,
        String variantRef,
        String exposureRef,
        String runRef,
        String subjectRef,
        String sessionRef,
        Instant exposedAt,
        long outcomeWindowSeconds,
        boolean clicked,
        boolean liked,
        boolean saved,
        boolean shared,
        boolean fallbackObserved,
        List<String> outcomeEventRefs,
        String sourceCheckpointRef,
        long sourceEventCount,
        String sourceLineageFingerprint,
        String projectionRecordFingerprint) implements ProjectionRecord {

    public ExperimentOutcomeInputProjection {
        recordRef = ProjectionEngineSupport.requireReference(recordRef, "recordRef");
        experimentRef = ProjectionEngineSupport.requireReference(experimentRef, "experimentRef");
        experimentVersion = ProjectionEngineSupport.requireVersion(experimentVersion, "experimentVersion");
        variantRef = ProjectionEngineSupport.requireToken(variantRef, "variantRef", 32);
        exposureRef = ProjectionEngineSupport.requireReference(exposureRef, "exposureRef");
        runRef = ProjectionEngineSupport.requireReference(runRef, "runRef");
        subjectRef = ProjectionEngineSupport.requireSubject(subjectRef, "subjectRef");
        sessionRef = ProjectionEngineSupport.requireReference(sessionRef, "sessionRef");
        Objects.requireNonNull(exposedAt, "exposedAt");
        if (outcomeWindowSeconds != 604_800L) {
            throw new IllegalArgumentException("outcomeWindowSeconds must be seven days");
        }
        outcomeEventRefs = List.copyOf(Objects.requireNonNull(outcomeEventRefs, "outcomeEventRefs"));
        for (String ref : outcomeEventRefs) {
            ProjectionEngineSupport.requireReference(ref, "outcomeEventRef");
        }
        sourceCheckpointRef = ProjectionEngineSupport.requireReference(
                sourceCheckpointRef, "sourceCheckpointRef");
        if (sourceEventCount < 1 || sourceEventCount < outcomeEventRefs.size()) {
            throw new IllegalArgumentException("sourceEventCount must include exposure and outcome lineage");
        }
        sourceLineageFingerprint = ProjectionEngineSupport.requireFingerprint(
                sourceLineageFingerprint, "sourceLineageFingerprint");
        projectionRecordFingerprint = ProjectionEngineSupport.requireFingerprint(
                projectionRecordFingerprint, "projectionRecordFingerprint");
    }

    @Override
    public String projectionName() {
        return ProjectionDefinition.OUTCOME_NAME;
    }

    @Override
    public Map<String, Object> canonicalFields() {
        LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
        fields.put("projectionName", projectionName());
        fields.put("experimentRef", experimentRef);
        fields.put("experimentVersion", experimentVersion);
        fields.put("variantRef", variantRef);
        fields.put("exposureRef", exposureRef);
        fields.put("runRef", runRef);
        fields.put("subjectRef", subjectRef);
        fields.put("sessionRef", sessionRef);
        fields.put("exposedAt", exposedAt);
        fields.put("outcomeWindowSeconds", outcomeWindowSeconds);
        fields.put("clicked", clicked);
        fields.put("liked", liked);
        fields.put("saved", saved);
        fields.put("shared", shared);
        fields.put("fallbackObserved", fallbackObserved);
        fields.put("outcomeEventRefs", outcomeEventRefs);
        fields.put("sourceCheckpointRef", sourceCheckpointRef);
        fields.put("sourceEventCount", sourceEventCount);
        fields.put("sourceLineageFingerprint", sourceLineageFingerprint);
        return Map.copyOf(fields);
    }
}
