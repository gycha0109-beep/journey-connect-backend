package com.jc.backend.recommendation.dataadoption.reconciliation;

import java.time.Instant;

public final class SyntheticIdentityPolicy {
    public Rca1Contracts.IdentityDecision validate(Rca1Contracts.SyntheticIdentityBinding binding, Instant referenceTime) {
        if (binding.status() == Rca1Contracts.IdentityStatus.MISMATCHED) {
            return denied(Rca1Contracts.Classification.IDENTITY_SCHEME_MISMATCH, "MISMATCHED");
        }
        if (binding.status() != Rca1Contracts.IdentityStatus.VALID) {
            return denied(Rca1Contracts.Classification.IDENTITY_MAPPING_REQUIRED, binding.status().name());
        }
        if (binding.deleted()) return denied(Rca1Contracts.Classification.IDENTITY_MAPPING_REQUIRED, "DELETED");
        if (!binding.validUntil().isAfter(referenceTime)) return denied(Rca1Contracts.Classification.IDENTITY_MAPPING_REQUIRED, "EXPIRED");
        if (!Rca1Contracts.PURPOSE.equals(binding.purpose())) return denied(Rca1Contracts.Classification.IDENTITY_MAPPING_REQUIRED, "UNAUTHORIZED_PURPOSE");
        if (!Rca1Contracts.CALLER.equals(binding.caller())) return denied(Rca1Contracts.Classification.IDENTITY_MAPPING_REQUIRED, "UNAUTHORIZED_CALLER");
        if (!binding.subjectRef().startsWith("synthetic-subject:") || !binding.userRef().startsWith("synthetic-user:")) {
            return denied(Rca1Contracts.Classification.IDENTITY_SCHEME_MISMATCH, "INVALID_SCHEME");
        }
        return new Rca1Contracts.IdentityDecision(true, Rca1Contracts.Classification.MATCH_EXACT, "VALID");
    }

    private static Rca1Contracts.IdentityDecision denied(Rca1Contracts.Classification classification, String status) {
        return new Rca1Contracts.IdentityDecision(false, classification, status);
    }
}
