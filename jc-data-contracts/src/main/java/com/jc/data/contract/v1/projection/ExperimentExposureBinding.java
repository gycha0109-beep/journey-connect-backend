package com.jc.data.contract.v1.projection;

import java.time.Instant;
import java.util.Objects;

public record ExperimentExposureBinding(
        String authorityId,
        String experimentRef,
        String experimentVersion,
        String assignmentRef,
        String exposureRef,
        String runRef,
        String sourceUserRef,
        String targetSubjectRef,
        String sessionRef,
        String variantRef,
        Instant exposedAt,
        String exposureFingerprint,
        boolean fallbackObserved) {

    public static final String AUTHORITY = "recommendation_p2_experiment_exposure";

    public ExperimentExposureBinding {
        authorityId = ProjectionEngineSupport.requireToken(authorityId, "authorityId", 96);
        experimentRef = ProjectionEngineSupport.requireReference(experimentRef, "experimentRef");
        experimentVersion = ProjectionEngineSupport.requireVersion(experimentVersion, "experimentVersion");
        assignmentRef = ProjectionEngineSupport.requireReference(assignmentRef, "assignmentRef");
        exposureRef = ProjectionEngineSupport.requireReference(exposureRef, "exposureRef");
        runRef = ProjectionEngineSupport.requireReference(runRef, "runRef");
        sourceUserRef = ProjectionEngineSupport.requireUser(sourceUserRef, "sourceUserRef");
        targetSubjectRef = ProjectionEngineSupport.requireSubject(targetSubjectRef, "targetSubjectRef");
        sessionRef = ProjectionEngineSupport.requireReference(sessionRef, "sessionRef");
        variantRef = ProjectionEngineSupport.requireToken(variantRef, "variantRef", 32);
        Objects.requireNonNull(exposedAt, "exposedAt");
        exposureFingerprint = ProjectionEngineSupport.requireFingerprint(
                exposureFingerprint, "exposureFingerprint");
    }
}
