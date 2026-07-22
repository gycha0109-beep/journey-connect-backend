package com.jc.data.contract.v1.projection;

public record ProjectionIdentifiers(String runRef, String snapshotRef) {
    public ProjectionIdentifiers {
        runRef = ProjectionEngineSupport.requireReference(runRef, "runRef");
        snapshotRef = ProjectionEngineSupport.requireReference(snapshotRef, "snapshotRef");
    }
}
