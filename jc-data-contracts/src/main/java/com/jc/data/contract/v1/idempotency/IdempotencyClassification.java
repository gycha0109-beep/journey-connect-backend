package com.jc.data.contract.v1.idempotency;

public enum IdempotencyClassification {
    NEW,
    DUPLICATE,
    CONFLICT
}
