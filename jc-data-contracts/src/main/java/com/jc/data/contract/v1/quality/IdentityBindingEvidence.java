package com.jc.data.contract.v1.quality;

import com.jc.data.contract.v1.projection.IdentityBinding;
import java.util.Objects;

public record IdentityBindingEvidence(
        IdentityBinding binding,
        String authoritativeFingerprint,
        String sourceCheckpointRef,
        String projectionSubjectRef) {
    public IdentityBindingEvidence {
        Objects.requireNonNull(binding, "binding");
        authoritativeFingerprint = QualityContractSupport.fingerprint(authoritativeFingerprint, "authoritativeFingerprint");
        sourceCheckpointRef = QualityContractSupport.reference(sourceCheckpointRef, "sourceCheckpointRef");
        projectionSubjectRef = QualityContractSupport.reference(projectionSubjectRef, "projectionSubjectRef");
    }
}
