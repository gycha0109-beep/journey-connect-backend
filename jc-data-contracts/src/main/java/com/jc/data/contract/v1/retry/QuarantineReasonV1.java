package com.jc.data.contract.v1.retry;

import java.util.Arrays;
import java.util.Optional;

public enum QuarantineReasonV1 {
    SCHEMA_UNSUPPORTED("schema_unsupported"),
    SOURCE_HASH_MISMATCH("source_hash_mismatch"),
    SOURCE_BINDING_INVALID("source_binding_invalid"),
    PAYLOAD_UNMAPPABLE("payload_unmappable"),
    PAYLOAD_TOO_LARGE("payload_too_large"),
    PRIVACY_POLICY_VIOLATION("privacy_policy_violation"),
    PROJECTION_INVARIANT_FAILED("projection_invariant_failed"),
    RETRY_EXHAUSTED("retry_exhausted"),
    REPEATED_DETERMINISTIC_FAILURE("repeated_deterministic_failure"),
    LINEAGE_SOURCE_MISSING("lineage_source_missing"),
    MANUAL_HOLD("manual_hold"),
    UNCLASSIFIED_FAILURE("unclassified_failure");

    private final String wireValue;

    QuarantineReasonV1(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static Optional<QuarantineReasonV1> fromWire(String value) {
        return Arrays.stream(values()).filter(item -> item.wireValue.equals(value)).findFirst();
    }

    public static QuarantineReasonV1 fromFailure(RetryFailureClassV1 failureClass) {
        return switch (failureClass) {
            case SCHEMA_UNSUPPORTED -> SCHEMA_UNSUPPORTED;
            case SOURCE_HASH_MISMATCH -> SOURCE_HASH_MISMATCH;
            case SOURCE_BINDING_INVALID -> SOURCE_BINDING_INVALID;
            case PAYLOAD_UNMAPPABLE -> PAYLOAD_UNMAPPABLE;
            case PAYLOAD_TOO_LARGE -> PAYLOAD_TOO_LARGE;
            case PRIVACY_POLICY_VIOLATION -> PRIVACY_POLICY_VIOLATION;
            case PROJECTION_INVARIANT_FAILED -> PROJECTION_INVARIANT_FAILED;
            case LINEAGE_SOURCE_MISSING -> LINEAGE_SOURCE_MISSING;
            case MANUAL_HOLD -> MANUAL_HOLD;
            default -> UNCLASSIFIED_FAILURE;
        };
    }
}
