package com.jc.data.contract.v1.idempotency;

import com.jc.data.contract.v1.identity.References;
import com.jc.data.contract.v1.validation.DataValidationErrorCode;

public record IdempotencyDecisionV1(
        IdempotencyClassification classification,
        References.EventId existingEventId,
        DataValidationErrorCode errorCode) {
    public IdempotencyDecisionV1 {
        if (classification == IdempotencyClassification.DUPLICATE && existingEventId == null) {
            throw new IllegalArgumentException("duplicate requires existing event reference");
        }
        if (classification == IdempotencyClassification.CONFLICT
                && errorCode != DataValidationErrorCode.IDEMPOTENCY_CONFLICT) {
            throw new IllegalArgumentException("conflict requires IDEMPOTENCY_CONFLICT");
        }
        if (classification == IdempotencyClassification.NEW && (existingEventId != null || errorCode != null)) {
            throw new IllegalArgumentException("new decision cannot expose existing event or error");
        }
    }
}
