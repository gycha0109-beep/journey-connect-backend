package com.jc.intelligence.contract.v1.identity;

import com.jc.intelligence.contract.v1.validation.ContractChecks;
import com.jc.intelligence.contract.v1.validation.IntelligenceValidationErrorCode;

public record SubjectRef(IdentitySchemeId schemeId, String value) {
    public SubjectRef {
        java.util.Objects.requireNonNull(schemeId, "schemeId");
        ContractChecks.requireText(
                value,
                "subjectRef",
                IntelligenceValidationErrorCode.INTELLIGENCE_SUBJECT_REF_INVALID);
        switch (schemeId) {
            case PLATFORM_SUBJECT_V1 -> {
                if (!value.matches("subject:[A-Za-z0-9][A-Za-z0-9._~\\-]{0,127}")) {
                    throw ContractChecks.invalid(
                            IntelligenceValidationErrorCode.INTELLIGENCE_SUBJECT_REF_INVALID,
                            "platform_subject_v1 requires subject:<opaque-id>");
                }
            }
            case LEGACY_USER_NUMERIC_V1 -> {
                if (!value.matches("user:[1-9][0-9]*")) {
                    throw ContractChecks.invalid(
                            IntelligenceValidationErrorCode.INTELLIGENCE_SUBJECT_REF_INVALID,
                            "legacy_user_numeric_v1 requires user:<positive-numeric-id>");
                }
                try {
                    long numericId = Long.parseLong(value.substring("user:".length()));
                    if (numericId <= 0L) {
                        throw new NumberFormatException("not positive");
                    }
                } catch (NumberFormatException exception) {
                    throw ContractChecks.invalid(
                            IntelligenceValidationErrorCode.INTELLIGENCE_SUBJECT_REF_INVALID,
                            "legacy user identifier is outside the positive long range");
                }
            }
        }
    }

    public static SubjectRef platformSubject(String opaqueId) {
        return new SubjectRef(IdentitySchemeId.PLATFORM_SUBJECT_V1, "subject:" + opaqueId);
    }

    public static SubjectRef legacyUser(long userId) {
        if (userId <= 0L) {
            throw ContractChecks.invalid(
                    IntelligenceValidationErrorCode.INTELLIGENCE_SUBJECT_REF_INVALID,
                    "legacy user identifier must be positive");
        }
        return new SubjectRef(IdentitySchemeId.LEGACY_USER_NUMERIC_V1, "user:" + userId);
    }

    @Override
    public String toString() {
        return value;
    }
}
