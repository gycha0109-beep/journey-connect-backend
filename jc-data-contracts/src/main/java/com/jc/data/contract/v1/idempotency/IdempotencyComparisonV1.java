package com.jc.data.contract.v1.idempotency;

import com.jc.data.contract.v1.identity.References;
import com.jc.data.contract.v1.validation.DataValidationErrorCode;
import com.jc.data.contract.v1.validation.ValidationError;
import com.jc.data.contract.v1.validation.ValidationResult;
import java.util.ArrayList;

public final class IdempotencyComparisonV1 {
    public ValidationResult<IdempotencyDecisionV1> compare(
            References.IdempotencyKey existingKey,
            String existingFingerprint,
            References.EventId existingEventId,
            References.IdempotencyKey candidateKey,
            String candidateFingerprint) {
        ArrayList<ValidationError> errors = new ArrayList<>();
        if (existingKey == null || candidateKey == null) {
            errors.add(new ValidationError(
                    DataValidationErrorCode.IDEMPOTENCY_KEY_INVALID, "idempotencyKey", "keys required"));
        }
        if (existingFingerprint == null || existingFingerprint.isBlank()
                || candidateFingerprint == null || candidateFingerprint.isBlank()) {
            errors.add(new ValidationError(
                    DataValidationErrorCode.FINGERPRINT_FAILURE, "fingerprint", "opaque fingerprints required"));
        }
        if (existingEventId == null) {
            errors.add(new ValidationError(
                    DataValidationErrorCode.MALFORMED_ID, "existingEventId", "existing event required"));
        }
        if (!errors.isEmpty()) {
            return ValidationResult.invalid(errors);
        }
        if (!existingKey.equals(candidateKey)) {
            return ValidationResult.valid(new IdempotencyDecisionV1(
                    IdempotencyClassification.NEW, null, null));
        }
        if (existingFingerprint.equals(candidateFingerprint)) {
            return ValidationResult.valid(new IdempotencyDecisionV1(
                    IdempotencyClassification.DUPLICATE, existingEventId, null));
        }
        return ValidationResult.valid(new IdempotencyDecisionV1(
                IdempotencyClassification.CONFLICT, existingEventId, DataValidationErrorCode.IDEMPOTENCY_CONFLICT));
    }
}
