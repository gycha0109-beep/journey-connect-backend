package com.jc.data.contract.v1.projection;

public record IdentityBinding(
        String sourceIdentityRef,
        String targetSubjectRef,
        String bindingVersion,
        String bindingSource,
        String bindingFingerprint,
        String bindingScope) {

    public IdentityBinding {
        sourceIdentityRef = ProjectionEngineSupport.requireIdentity(sourceIdentityRef, "sourceIdentityRef");
        targetSubjectRef = ProjectionEngineSupport.requireSubject(targetSubjectRef, "targetSubjectRef");
        bindingVersion = ProjectionEngineSupport.requireVersion(bindingVersion, "bindingVersion");
        bindingSource = ProjectionEngineSupport.requireToken(bindingSource, "bindingSource", 96);
        bindingFingerprint = ProjectionEngineSupport.requireFingerprint(bindingFingerprint, "bindingFingerprint");
        bindingScope = ProjectionEngineSupport.requireToken(bindingScope, "bindingScope", 96);
    }
}
