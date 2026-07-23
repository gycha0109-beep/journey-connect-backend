package com.jc.data.contract.v1.integration;

import java.util.List;

public final class CrossTrackIdentityValidator {
    public List<CrossTrackIntegrationCheck> validate(CrossTrackIntegrationContext context, int order) {
        CrossTrackIdentityBinding binding = context.identityBinding();
        if (binding == null) return List.of(CheckFactory.check(order, "identity.binding.present", CrossTrackIntegrationScope.IDENTITY_BOUNDARY,
                context.sourceSnapshot().identityNamespace(), context.targetContract().identityNamespace(), "explicit binding", "missing", false,
                CrossTrackIntegrationFailure.CROSS_TRACK_IDENTITY_BINDING_MISSING, true, false));
        if (!IntegrationSupport.namespaceSupported(binding.sourceIdentityRef()) || !IntegrationSupport.namespaceSupported(binding.targetIdentityRef())) {
            return List.of(CheckFactory.check(order, "identity.namespace.supported", CrossTrackIntegrationScope.IDENTITY_BOUNDARY,
                    binding.sourceIdentityRef(), binding.targetIdentityRef(), "supported namespace", "unsupported", false,
                    CrossTrackIntegrationFailure.CROSS_TRACK_IDENTITY_NAMESPACE_MISMATCH, true, false));
        }
        if (!binding.authoritativeFingerprint().equals(binding.bindingFingerprint())) return List.of(CheckFactory.check(order,
                "identity.binding.fingerprint", CrossTrackIntegrationScope.IDENTITY_BOUNDARY, binding.bindingFingerprint(),
                binding.authoritativeFingerprint(), binding.authoritativeFingerprint(), binding.bindingFingerprint(), false,
                CrossTrackIntegrationFailure.CROSS_TRACK_IDENTITY_FINGERPRINT_MISMATCH, true, false));
        boolean endpointMatches = binding.sourceIdentityRef().equals(context.sourceSnapshot().identityNamespace())
                || binding.targetIdentityRef().equals(context.sourceSnapshot().identityNamespace());
        if (!endpointMatches) return List.of(CheckFactory.check(order, "identity.snapshot_binding",
                CrossTrackIntegrationScope.IDENTITY_BOUNDARY, context.sourceSnapshot().identityNamespace(),
                binding.targetIdentityRef(), "one binding endpoint equals snapshot identity", "mismatch", false,
                CrossTrackIntegrationFailure.CROSS_TRACK_IDENTITY_NAMESPACE_MISMATCH, true, false));
        if (binding.automaticMerge()) return List.of(CheckFactory.check(order, "identity.automatic_merge", CrossTrackIntegrationScope.IDENTITY_BOUNDARY,
                binding.sourceIdentityRef(), binding.targetIdentityRef(), "false", "true", false,
                CrossTrackIntegrationFailure.CROSS_TRACK_IDENTITY_AUTOMATIC_MERGE_DETECTED, true, false));
        if (!binding.bindingScope().equals("cross-track-integration")) return List.of(CheckFactory.check(order, "identity.binding.scope",
                CrossTrackIntegrationScope.IDENTITY_BOUNDARY, binding.bindingScope(), "cross-track-integration", "cross-track-integration",
                binding.bindingScope(), false, CrossTrackIntegrationFailure.CROSS_TRACK_IDENTITY_SCOPE_MISMATCH, true, false));
        if (!binding.ownerTrack().equals("Data")) return List.of(CheckFactory.check(order, "identity.binding.authority",
                CrossTrackIntegrationScope.IDENTITY_BOUNDARY, binding.ownerTrack(), "Data", "Data", binding.ownerTrack(), false,
                CrossTrackIntegrationFailure.CROSS_TRACK_IDENTITY_AUTHORITY_VIOLATION, true, false));
        return List.of(CheckFactory.check(order, "identity.binding.valid", CrossTrackIntegrationScope.IDENTITY_BOUNDARY,
                binding.sourceIdentityRef(), binding.targetIdentityRef(), "explicit/versioned/scoped", "explicit/versioned/scoped", true,
                CrossTrackIntegrationFailure.CROSS_TRACK_IDENTITY_BINDING_INVALID, true, false));
    }
}
