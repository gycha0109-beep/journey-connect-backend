package com.jc.data.contract.v1.projection;

public enum ProjectionFailureCode {
    UNSUPPORTED_PROJECTION_SCHEMA("unsupported_projection_schema"),
    UNSUPPORTED_SOURCE_SCHEMA("unsupported_source_schema"),
    SOURCE_CHECKPOINT_INVALID("source_checkpoint_invalid"),
    SOURCE_EVENT_MISSING("source_event_missing"),
    SOURCE_FINGERPRINT_MISMATCH("source_fingerprint_mismatch"),
    ADAPTER_EVIDENCE_MISSING("adapter_evidence_missing"),
    ADAPTER_EVIDENCE_CONFLICTED("adapter_evidence_conflicted"),
    ADAPTER_EVIDENCE_REJECTED("adapter_evidence_rejected"),
    IDENTITY_BINDING_REQUIRED("identity_binding_required"),
    IDENTITY_BINDING_INVALID("identity_binding_invalid"),
    IDENTITY_NAMESPACE_CONFLICT("identity_namespace_conflict"),
    EXPOSURE_BINDING_MISSING("exposure_binding_missing"),
    EXPOSURE_BINDING_INVALID("exposure_binding_invalid"),
    OUTCOME_WINDOW_VIOLATION("outcome_window_violation"),
    PROJECTION_INVARIANT_FAILED("projection_invariant_failed"),
    LINEAGE_INCOMPLETE("lineage_incomplete"),
    SNAPSHOT_FINGERPRINT_CONFLICT("snapshot_fingerprint_conflict"),
    PRIVACY_POLICY_VIOLATION("privacy_policy_violation"),
    UNCLASSIFIED_PROJECTION_FAILURE("unclassified_projection_failure");

    private final String wireValue;

    ProjectionFailureCode(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
