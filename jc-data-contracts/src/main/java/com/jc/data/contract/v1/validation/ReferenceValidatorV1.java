package com.jc.data.contract.v1.validation;

import com.jc.data.contract.v1.identity.IdentityScheme;
import com.jc.data.contract.v1.identity.References;
import java.util.List;
import java.util.function.Function;

public final class ReferenceValidatorV1 {
    private ReferenceValidatorV1() {
    }

    public static ValidationResult<String> eventId(String value) {
        return validate(value, "eventId", DataValidationErrorCode.INVALID_EVENT_ID, References.EventId::new);
    }

    public static ValidationResult<String> sessionRef(String value) {
        return validate(value, "sessionRef", DataValidationErrorCode.INVALID_SESSION_REF, References.SessionRef::new);
    }

    public static ValidationResult<String> requestRef(String value) {
        return validate(value, "requestId", DataValidationErrorCode.INVALID_REQUEST_REF, References.RequestRef::new);
    }

    public static ValidationResult<String> correlationRef(String value) {
        return validate(value, "correlationId", DataValidationErrorCode.INVALID_CORRELATION_REF,
                References.CorrelationRef::new);
    }

    public static ValidationResult<String> causationRef(String value) {
        return validate(value, "causationId", DataValidationErrorCode.INVALID_CAUSATION_REF,
                References.CausationRef::new);
    }

    public static ValidationResult<String> entityRef(String value) {
        return validate(value, "entityRef", DataValidationErrorCode.INVALID_ENTITY_REF, References.EntityRef::new);
    }

    public static ValidationResult<String> idempotencyKey(String value) {
        return validate(value, "idempotencyKey", DataValidationErrorCode.IDEMPOTENCY_KEY_INVALID,
                References.IdempotencyKey::new);
    }

    public static ValidationResult<String> subjectRef(IdentityScheme scheme, String value) {
        if (scheme == null) {
            return ValidationResult.invalid(List.of(new ValidationError(
                    DataValidationErrorCode.IDENTITY_NAMESPACE_MISSING, "subjectRef", "identity scheme required")));
        }
        try {
            new References.SubjectRef(scheme, value);
            return ValidationResult.valid(value);
        } catch (RuntimeException exception) {
            DataValidationErrorCode code = value != null && value.matches("[1-9][0-9]*")
                    ? DataValidationErrorCode.RAW_NUMERIC_IDENTITY_FORBIDDEN
                    : DataValidationErrorCode.INVALID_SUBJECT_REF;
            return ValidationResult.invalid(List.of(new ValidationError(code, "subjectRef", "invalid subject reference")));
        }
    }

    public static ValidationResult<String> canonicalActorRef(String value) {
        ValidationResult<String> subject = subjectRef(IdentityScheme.PLATFORM_SUBJECT_V1, value);
        if (subject.isValid()) {
            return subject;
        }
        if (value != null && value.startsWith("user:")) {
            return ValidationResult.invalid(List.of(new ValidationError(
                    DataValidationErrorCode.IDENTITY_NAMESPACE_MISMATCH,
                    "actorRef", "legacy identity cannot be used as canonical Data actor")));
        }
        return ValidationResult.invalid(List.of(new ValidationError(
                DataValidationErrorCode.INVALID_ACTOR_REF, "actorRef", "invalid canonical actor reference")));
    }

    private static ValidationResult<String> validate(
            String value,
            String field,
            DataValidationErrorCode code,
            Function<String, ?> constructor) {
        try {
            constructor.apply(value);
            return ValidationResult.valid(value);
        } catch (RuntimeException exception) {
            return ValidationResult.invalid(List.of(new ValidationError(code, field, "invalid reference value")));
        }
    }
}
