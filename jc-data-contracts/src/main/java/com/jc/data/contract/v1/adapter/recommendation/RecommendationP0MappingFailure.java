package com.jc.data.contract.v1.adapter.recommendation;

public enum RecommendationP0MappingFailure {
    UNSUPPORTED_EVENT_TYPE("unsupported_event_type", "schema_unsupported", false),
    UNSUPPORTED_SCHEMA_VERSION("unsupported_schema_version", "schema_unsupported", false),
    IDENTITY_MAPPING_REQUIRED("identity_mapping_required", "source_binding_invalid", false),
    MISSING_REQUIRED_REFERENCE("missing_required_reference", "source_binding_invalid", false),
    MISSING_EXPOSURE_REFERENCE("missing_exposure_reference", "source_binding_invalid", false),
    PAYLOAD_UNMAPPABLE("payload_unmappable", "payload_unmappable", false),
    TIMESTAMP_INVALID("timestamp_invalid", "source_binding_invalid", false),
    SOURCE_FINGERPRINT_MISMATCH("source_fingerprint_mismatch", "source_hash_mismatch", false),
    TARGET_CONTRACT_VIOLATION("target_contract_violation", "projection_invariant_failed", false),
    PRIVACY_POLICY_VIOLATION("privacy_policy_violation", "privacy_policy_violation", false),
    EXPOSURE_AUTHORITY_CONFLICT("exposure_authority_conflict", "source_binding_invalid", false),
    ADAPTER_INVARIANT_FAILED("adapter_invariant_failed", "projection_invariant_failed", false),
    UNCLASSIFIED_MAPPING_FAILURE("unclassified_mapping_failure", "unclassified_failure", false);

    private final String wireValue;
    private final String quarantineReason;
    private final boolean retryable;

    RecommendationP0MappingFailure(String wireValue, String quarantineReason, boolean retryable) {
        this.wireValue = wireValue;
        this.quarantineReason = quarantineReason;
        this.retryable = retryable;
    }

    public String wireValue() {
        return wireValue;
    }

    public String quarantineReason() {
        return quarantineReason;
    }

    public boolean retryable() {
        return retryable;
    }
}
