package com.jc.data.contract.v1.integration;

public record CrossTrackIdentityBinding(
        String sourceIdentityRef,
        String targetIdentityRef,
        String bindingVersion,
        String bindingSource,
        String bindingFingerprint,
        String authoritativeFingerprint,
        String bindingScope,
        String ownerTrack,
        boolean automaticMerge) {
    public CrossTrackIdentityBinding {
        sourceIdentityRef = IntegrationSupport.reference(sourceIdentityRef, "sourceIdentityRef");
        targetIdentityRef = IntegrationSupport.reference(targetIdentityRef, "targetIdentityRef");
        bindingVersion = IntegrationSupport.version(bindingVersion, "bindingVersion");
        bindingSource = IntegrationSupport.text(bindingSource, "bindingSource");
        bindingFingerprint = IntegrationSupport.fingerprint(bindingFingerprint, "bindingFingerprint");
        authoritativeFingerprint = IntegrationSupport.fingerprint(authoritativeFingerprint, "authoritativeFingerprint");
        bindingScope = IntegrationSupport.text(bindingScope, "bindingScope");
        ownerTrack = IntegrationSupport.text(ownerTrack, "ownerTrack");
    }
}
