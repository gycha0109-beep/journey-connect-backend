package com.jc.data.contract.v1.retry;

import java.util.Arrays;
import java.util.Optional;

public enum RetryFailureClassV1 {
    DATABASE_LOCK_TIMEOUT("database_lock_timeout", true),
    SERIALIZATION_FAILURE("serialization_failure", true),
    DEPENDENCY_UNAVAILABLE("dependency_unavailable", true),
    WORKER_INTERRUPTED("worker_interrupted", true),
    RATE_LIMITED("rate_limited", true),
    SCHEMA_UNSUPPORTED("schema_unsupported", false),
    SOURCE_HASH_MISMATCH("source_hash_mismatch", false),
    SOURCE_BINDING_INVALID("source_binding_invalid", false),
    PAYLOAD_UNMAPPABLE("payload_unmappable", false),
    PAYLOAD_TOO_LARGE("payload_too_large", false),
    PRIVACY_POLICY_VIOLATION("privacy_policy_violation", false),
    PROJECTION_INVARIANT_FAILED("projection_invariant_failed", false),
    LINEAGE_SOURCE_MISSING("lineage_source_missing", false),
    MANUAL_HOLD("manual_hold", false),
    UNCLASSIFIED_FAILURE("unclassified_failure", false);

    private final String wireValue;
    private final boolean automaticallyRetryable;

    RetryFailureClassV1(String wireValue, boolean automaticallyRetryable) {
        this.wireValue = wireValue;
        this.automaticallyRetryable = automaticallyRetryable;
    }

    public String wireValue() {
        return wireValue;
    }

    public boolean automaticallyRetryable() {
        return automaticallyRetryable;
    }

    public static Optional<RetryFailureClassV1> fromWire(String value) {
        return Arrays.stream(values()).filter(item -> item.wireValue.equals(value)).findFirst();
    }

    public static RetryFailureClassV1 fromWireFailClosed(String value) {
        return fromWire(value).orElse(UNCLASSIFIED_FAILURE);
    }
}
